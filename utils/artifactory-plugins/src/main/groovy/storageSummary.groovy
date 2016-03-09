import groovy.xml.MarkupBuilder
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

/**
 * This plugin provides a simplified version of the "Storage Summary" page in the Artifactory web UI, for those repos
 * for which the user has read permission.  It does not account for the fact that the same artifacts can appear in more
 * than one repo, and don't really contribute much disk use in that case (and I can't see a way to do that).
 */

/**
 *
 * Globally bound variables:
 *
 * log (org.slf4j.Logger)
 * repositories (org.artifactory.repo.Repositories)
 * security (org.artifactory.security.Security)
 * searches (org.artifactory.search.Searches) [since: 2.3.4]
 * builds (org.artifactory.build.Builds) [since 2.5.2]
 *
 * ctx (org.artifactory.spring.InternalArtifactoryContext) - NOT A PUBLIC API - FOR INTERNAL USE ONLY!
 */

executions {
    // NOTE 2016-01-20 HughG: I want to read the set of groups from a file, but the plugin API gives me no way to
    // get the plugin directory or something like that.  From experimentation, the current directory when the
    // "executions" block is executed is the "bin" folder of the installation.
    final File ARTIFACTORY_ROOT = new File("..")
    final Set PLUGIN_GROUPS = new File(ARTIFACTORY_ROOT, "etc/plugins/storageSummary.groups.txt").readLines().toSet()

    /**
     * This execution is named 'storageSummary' and it can be invoked via the usual Artifactory REST API.
     * The expected (and mandatory) parameter is MIME-style 'Content-Type' describing the format in which to return the
     * stats.  (In fact Artifactory always uses text/plain, but the client can ignore that.)
     *
     * You may need to change the list of {@code groups:} below to suit your local setup.
     *
     * curl -u username:password "http://localhost:8081/artifactory/api/plugins/execute/storageSummary?params=Content-Type=text/xml"
     */
    storageSummary(httpMethod: 'GET', groups: PLUGIN_GROUPS) { Map params ->
        try {
            status = 200

            // Gather the artifact count and size for local and remote repos, both for all repos and for only those the
            // current user is allowed to see.
            Collection<RepoStats> allRepoStats = []
            Collection<RepoStats> userVisibleRepoStats = []
            addStatsForUserVisibleRepos(
                repositories.getLocalRepositories(),
                allRepoStats,
                userVisibleRepoStats
            )
            addStatsForUserVisibleRepos(
                repositories.getRemoteRepositories().collect { "${it}-cache" },
                allRepoStats,
                userVisibleRepoStats
            )
            // Calculate totals; also used to give percentages.
            RepoStats userVisibleTotal = userVisibleRepoStats.inject(new RepoStats("_USER"), { a, b -> a + b })
            RepoStats total = allRepoStats.inject(new RepoStats("TOTAL"), { a, b -> a + b })
            RepoStats otherReposTotal = new RepoStats("OTHER") + total - userVisibleTotal
            userVisibleRepoStats << otherReposTotal
            userVisibleRepoStats << total

            // Write out in the requested format.
            StringWriter sw = new StringWriter()
            final List contentTypes = params['Content-Type'] as List
            final contentType = contentTypes?.get(0)
            switch (contentType) {
                case "application/xml":
                case "text/xml":
                case "text/html":
                    outputXhtml(sw, userVisibleRepoStats, total)
                    break;

                case "text/vnd.holygradle-it360-table":
                    outputIt360Table(sw, userVisibleRepoStats, total)
                    break;

                default:
                    sw.print("Unknown Content-Type '${contentType}'")
                    status = 400
                    break;
            }

            message = sw.toString()
        } catch (e) {
            log.error 'Failed to execute plugin', e
            message = e.message
            status = 500
        }
    }
}

/**
 * A simple holder for repo stats, with a couple of convenience methods.
 */
class RepoStats {
    public final String name;
    public final long count;
    public final long size;

    public RepoStats(String name, long count, long size) {
        this.name = name
        this.count = count
        this.size = size
    }

    public RepoStats(String name) {
        this(name, 0, 0)
    }

    public String percentageOfAsString(RepoStats other) {
        final double percentage = (double) size / (double) other.size * 100.0d
        return String.format('%.2f', percentage)
    }

    public double getSizeInMegabytes() {
        return roundToTwoDecimalPlaces(size / 1024.0d)
    }

    public double getSizeInGigabytes() {
        return roundToTwoDecimalPlaces(sizeInMegabytes / 1024.0d)
    }

    public RepoStats plus(RepoStats other) {
        return new RepoStats(name, count + other.count, size + other.size)
    }

    public RepoStats minus(RepoStats other) {
        return new RepoStats(name, count - other.count, size - other.size)
    }

    private static double roundToTwoDecimalPlaces(double value) {
        Math.round(value*100)/100.0d
    }
}

/**
 * Collects size statistics for all named repos, and for the subset whose root folders are readable by the current user.
 *
 * @param allRepoStats A collection to be filled with stats on all repos
 * @param userVisibleRepoStats A collection to be filled with stats on user-visible repos
 * @param repoNames The names of the repos for which to gather stats.
 */
def void addStatsForUserVisibleRepos(
    List<String> repoNames,
    Collection<RepoStats> allRepoStats,
    Collection<RepoStats> userVisibleRepoStats
) {
    repoNames.each { String repoName ->
        RepoPath repoRoot = RepoPathFactory.create(repoName, "/")
        final stats = new RepoStats(
            repoName,
            repositories.getArtifactsCount(repoRoot),
            repositories.getArtifactsSize(repoRoot)
        )
        allRepoStats << stats
        if (security.canRead(repoRoot)) {
            userVisibleRepoStats << stats
        }
    }
}

/**
 * Outputs repo stats in the format described at
 * http://www.manageengine.com/it360/help/meitms/applications/help/monitors/script-monitoring.html
 *
 * @param sw A string writer to which to write output.
 * @param repoStats The collection of stats to write.
 * @param total The total for all stats (which may be more than are in the collection), for calculating percentages.
 */
private void outputIt360Table(StringWriter sw, Collection<RepoStats> repoStats, RepoStats total) {
    PrintWriter pw = new PrintWriter(sw)
    pw.println '"<--table repoStats starts-->"'
    pw.println 'Repo_Name Artifacts_Count Artifacts_Size Artifacts_Size_in_MB Artifacts_Size_in_GB Artifacts_Percentage'
    repoStats.each {
        pw.println "${it.name} ${it.count} ${it.size} ${it.sizeInMegabytes} ${it.sizeInGigabytes} ${it.percentageOfAsString(total)}"
    }
    pw.println '"<--table repoStats ends-->"'
}


/**
 * Outputs repo stats in XHTML format.
 *
 * @param sw A string writer to which to write output.
 * @param repoStats The collection of stats to write.
 * @param total The total for all stats (which may be more than are in the collection), for calculating percentages.
 */
private void outputXhtml(StringWriter sw, Collection<RepoStats> repoStats, RepoStats total) {
    sw.println('<?xml version="1.0" encoding="UTF-8"?>')
    def build = new MarkupBuilder(sw)
    build.html {
        body {
            table {
                tr {
                    th 'Repo_Name'
                    th 'Artifacts_Count'
                    th 'Artifacts_Size'
                    th 'Artifacts_Size_in_MB'
                    th 'Artifacts_Size_in_GB'
                    th 'Artifacts_Percentage'
                }
                repoStats.each { RepoStats stats ->
                    tr {
                        td stats.name
                        td stats.count
                        td stats.size
                        td stats.sizeInMegabytes
                        td stats.sizeInGigabytes
                        td stats.percentageOfAsString(total)
                    }
                }
            }
        }
    }
}
import groovy.xml.MarkupBuilder
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

/**
 * This plugin provides a table of modules listed by module name, showing the groups under which that module appears,
 * and which virtual repos (from a given set) you can find it in.  This is helpful because the ownership of some modules
 * (that is, the group/organisation part of the name) isn't always well known, or correctly set up for a package, and
 * can change over time (e.g., libtiff).
 *
 * TODO 2015-07-17 HughG: Probably make this return JSON and then put it behind an HTML page which renders from that.
 * Either that, or use Apache to change the Content-Type header for the result.  As it is, the output XHTML is returned
 * with type text/plain.
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
    /**
     * TODO 2015-07-17 HughG: doc
     *
     * curl -u username:password "http://localhost:8081/artifactory/api/plugins/execute/modulesByName"
     */
    modulesByName(httpMethod: 'GET', groups: ["readers"].toSet()) { Map params ->
        try {
            status = 200
            StringWriter sw = new StringWriter()

            // name -> org -> repos
            Map<String, Map<String, Collection<String>>> modulesByName =
                new HashMap().withDefault { new HashMap().withDefault { new ArrayList<String>() } }

            // TODO 2015-07-17 HughG: Sadly, the nested loop below always returns "nothing" for virtual repos.  We'll
            // have to query the virtual repo config and then query each nested repo (local, remote, or virtual),
            // reporting the results against the original virtual repo.  For efficiency we should only do this once per
            // underlying local/remote repo, even if we find it in the context of multiple origin virtual repos.
            // Another possible issue is, will asking for the children of a remote repo give you all remote contents or
            // only the ones already cached?

            // TODO 2015-07-17 HughG: Get this list from a config file, request param, or something.
            // List<String> repoNames = ["libs-release", "libs-snapshot"]
            List<String> repoNames = ["libs-release-local", "libs-snapshot-local"]
            
            for (String repoName in repoNames) {
                RepoPath root = RepoPathFactory.create(repoName, "/")
                for (ItemInfo org in repositories.getChildren(root)) {
                    for (ItemInfo name in repositories.getChildren(org.repoPath)) {
                        modulesByName[name.name][org.name] << repoName
                    }
                }
            }

            outputXhtml(sw, modulesByName)

            message = sw.toString()
        } catch (e) {
            log.error 'Failed to execute plugin', e
            message = e.message
            status = 500
        }
    }
}

/**
 * Outputs repo stats in XHTML format.
 *
 * @param sw A string writer to which to write output.
 * @param modulesByName The module info to write.
 */
private void outputXhtml(StringWriter sw, Map<String, Map<String, Collection<String>>> modulesByName) {
    sw.println('<?xml version="1.0" encoding="UTF-8"?>')
    def build = new MarkupBuilder(sw)
    build.html {
        body {
            table {
                tr {
                    th 'Name'
                    th 'Groups'
                    th 'Repositories'
                }
                modulesByName.keySet().sort(String.CASE_INSENSITIVE_ORDER).each { String name ->
                    Map<String, Collection<String>> reposByOrg = modulesByName[name]
                    // sw.println "${name} ..."
                    tr {
                        boolean firstOrg = true
                        reposByOrg.keySet().sort(String.CASE_INSENSITIVE_ORDER).each { String org ->
                            Collection<String> repos = reposByOrg[org]
                            // sw.println "${name} ${org} ..."
                            if (firstOrg) {
                                // sw.println "${name} ${org} ${repos.join(",")}"

                                td(rowspan: reposByOrg.size(), name)
                                firstOrg = false
                            }
                            td org
                            td repos.join(",")
                        }
                    }
                }
            }
        }
    }
}
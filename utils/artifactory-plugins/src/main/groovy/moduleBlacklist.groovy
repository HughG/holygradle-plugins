/**
 * This plugin "blacklists" modules (actually, artitrary path prefixes) from downloading, returning an HTTP "401 Gone"
 * status with a configurable message for each.  This is intended for use when a module must not be used but it is
 * undesirable to simply delete it, leaving users with no clue as to what to do instead.  Blacklisted paths are still
 * viewable in the Artifactory Web UI but any attempt to view or download the file content will fail.
 */

import org.artifactory.exception.CancelException

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

download {
    // NOTE 2016-01-20 HughG: I want to read the set of groups from a file, but the plugin API gives me no way to
    // get the plugin directory or something like that.  From experimentation, the current directory when the
    // "executions" block is executed is the "bin" folder of the installation.
    final File ARTIFACTORY_ROOT = new File("..")
    final Properties BLACKLIST_PATH_PREFIXES = new File(ARTIFACTORY_ROOT, "etc/plugins/conf/moduleBlacklist.properties")
        .withInputStream { input -> new Properties().with { load(input); it } }

    altResponse { request, responseRepoPath ->
        String path = responseRepoPath.path;
        def blacklistEntry = BLACKLIST_PATH_PREFIXES.find { path.startsWith(it.key) }
        if (blacklistEntry != null) {
            status = 410; // Return a 410: GONE status
            message = blacklistEntry.value;
        }
    }
}
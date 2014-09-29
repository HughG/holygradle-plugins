import org.artifactory.exception.CancelException
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath

/**
 * This plugin is a workaround for defect <https://www.jfrog.com/jira/browse/RTFACT-6377>.  It avoids getting a repo
 * into a state in which some files won't be backed up, by rejecting additions of files which would cause a name clash.
 * It won't detect whether you already have folders which clash, though.
 *
 * I tried quite hard to get this plugin to provide a useful error message in all cases, but it will just give a 500
 * with a semi-informative error in some cases.  This happens if you deploy a file for which one of its parent folders
 * has a clash.  This is because recursive creation of parents happens automatically, outside the scope of the handler
 * methods below, and if one of those fails, the 409 thrown by this plugin is caught and re-thrown as a 500.  An admin
 * can still see an informative message in the logs, though.
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

def List<ItemInfo> checkSiblings(RepoPath p) {
    List<ItemInfo> siblings = repositories.getChildren(p.parent)
    List<ItemInfo> clashes = siblings.findAll {
        log.info("    sibling " + it)
        final boolean clash = (p.path != it.repoPath.path && p.path.equalsIgnoreCase(it.repoPath.path))
        if (clash) {
            log.info("        CLASH!")
        }
        return clash
    }
    return clashes
}

def void checkPathForCaseClashes(RepoPath repoPath) {
    List<ItemInfo> clashes = []
    for (RepoPath path = repoPath; !path.root; path = path.parent) {
        log.info("Checking " + path)
        clashes += checkSiblings(path)
    }

    if (!clashes.empty) {
        throw new CancelException(
            "Path ${repoPath} is partly case-insensitive-equal to the following existing items, " +
                "which would interfere with backups due to <https://www.jfrog.com/jira/browse/RTFACT-6377>: " +
                clashes*.repoPath*.toString().join(", "),
            HttpURLConnection.HTTP_CONFLICT
        )
    }
}

storage {

    /**
     * Handle before create events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the original item being created.
     */
    beforeCreate { ItemInfo item ->
        checkPathForCaseClashes(item.repoPath)
    }

    /**
     * Handle before move events.
     *
     * Closure parameters:

     * item (org.artifactory.fs.ItemInfo) - the source item being moved.
     * targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the move.
     * properties (org.artifactory.md.Properties) - user specified properties to add to the item being moved.
     */
    beforeMove { ItemInfo item, RepoPath targetRepoPath, org.artifactory.md.Properties properties ->
        checkPathForCaseClashes(targetRepoPath)
    }

    /**
     * Handle before copy events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the source item being copied.
     * targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the copy.
     * properties (org.artifactory.md.Properties) - user specified properties to add to the item being moved.
     */
    beforeCopy { ItemInfo item, RepoPath targetRepoPath, org.artifactory.md.Properties properties ->
        checkPathForCaseClashes(targetRepoPath)
    }
}
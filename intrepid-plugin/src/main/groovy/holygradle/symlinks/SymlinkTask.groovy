package holygradle.symlinks

import holygradle.custom_gradle.util.Symlink
import org.gradle.api.DefaultTask
import org.gradle.api.Project

class SymlinkTask extends DefaultTask {
    private Map<File, File> entries = [:]

    /**
     * Throws an exception if {@code link} exists and is not a symlink.
     * @param link The potential link to check.
     * @param target The intended target for the link (for use in error message).
     */
    public static void checkIsSymlinkOrMissing(File link, File target) {
        final boolean linkExists = link.exists()
        final boolean isSymlink = Symlink.isJunctionOrSymlink(link)
        if (linkExists && !isSymlink) {
            throw new RuntimeException(
                "Could not create a symlink from '${link.path}' to '${target.path}' " +
                "because the former already exists and is not a symlink."
            )
        }
    }

    public void initialize() {
        Map<File, File> localEntries = entries // capture private for closure
        doFirst {
            localEntries.each { File linkDir, File targetDir ->
                checkIsSymlinkOrMissing(linkDir, targetDir)

                Symlink.rebuild(linkDir, targetDir)
            }
        }
    }

    public void addLink(File linkDir, File targetDir) {
        if (entries.containsKey(linkDir)) {
            throw new RuntimeException(
                "Cannot initialize for symlink from '${linkDir.path}' to '${targetDir.path}' " +
                "because a symlink has already been added from there to '${entries[linkDir].path}'"
            )
        }
        entries[linkDir] = targetDir
    }
}

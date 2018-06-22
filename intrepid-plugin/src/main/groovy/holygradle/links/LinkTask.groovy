package holygradle.links

import holygradle.io.Link
import org.gradle.api.DefaultTask

class LinkTask extends DefaultTask {
    private Map<File, File> entries = [:]

    /**
     * Throws an exception if {@code link} exists and is not a symlink.
     * @param link The potential link to check.
     * @param target The intended target for the link (for use in error message).
     */
    public static void checkIsLinkOrMissing(File link, File target) {
        final boolean linkExists = link.exists()
        final boolean isLink = Link.isLink(link)
        if (linkExists && !isLink) {
            throw new RuntimeException(
                    "Could not create a link from '${link.path}' to '${target.path}' " +
                            "because the former already exists and is not a link."
            )
        }
    }

    public void initialize() {
        Map<File, File> localEntries = entries // capture private for closure
        doFirst {
            localEntries.each { File linkDir, File targetDir ->
                Link.rebuild(linkDir, targetDir)
            }
        }
    }

    public void addLink(File linkDir, File targetDir) {
        if (entries.containsKey(linkDir)) {
            throw new RuntimeException(
                "Cannot initialize for link from '${linkDir.path}' to '${targetDir.path}' " +
                "because a link has already been added from there to '${entries[linkDir].path}'"
            )
        }
        entries[linkDir] = targetDir
    }
}

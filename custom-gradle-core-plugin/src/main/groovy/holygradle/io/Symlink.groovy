package holygradle.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

public class Symlink {
    public static boolean isJunctionOrSymlink(File file) throws IOException {
        Files.isSymbolicLink(Paths.get(file.path))
    }

    public static void delete(File link) {
        checkIsSymlinkOrMissing(link)

        if (isJunctionOrSymlink(link)) {
            link.delete()
        }
    }
    
    public static void rebuild(File link, File target) {
        checkIsSymlinkOrMissing(link)

        File canonicalLink = link.getCanonicalFile()
        
        // Delete the symlink if it exists
        delete(canonicalLink)
        
        // Make sure [link]'s parent directory exists
        final File linkParentDir = canonicalLink.parentFile
        if (linkParentDir != null) {
            if (!linkParentDir.exists() && !linkParentDir.mkdirs()) {
                throw new RuntimeException("Failed to create parent folder ${linkParentDir} for symlink ${canonicalLink.name}")
            }
        }

        // If [target] is relative, createSymbolicLink will create a link relative to [link] (as opposed to relative to
        // the current working directory) so we have to calculate this.
        File re_relativized_target = canonicalLink.toPath().resolveSibling(target.toPath()).toFile()

        if (!re_relativized_target.exists()) {
            throw new IOException("Cannot create link to non-existent target: from '${canonicalLink}' to '${target}'")
        }
        Files.createSymbolicLink(canonicalLink.toPath(), target.toPath())
    }

    /**
     * Throws an exception if {@code link} exists and is not a symlink.
     * @param link The potential link to check.
     */
    public static void checkIsSymlinkOrMissing(File link) {
        final boolean linkExists = link.exists()
        final boolean isSymlink = Symlink.isJunctionOrSymlink(link)
        if (linkExists && !isSymlink) {
            throw new RuntimeException(
                "Cannot not delete or create a symlink at '${link.path}' " +
                "because a folder or file already exists there and is not a symlink."
            )
        }
    }
}
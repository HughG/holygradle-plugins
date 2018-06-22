package holygradle.io

import java.nio.file.Files
import java.nio.file.Paths

public class Symlink {
    public static boolean isSymlink(File file) throws IOException {
        Files.isSymbolicLink(Paths.get(file.path))
    }

    public static void delete(File link) {
        // For simplicity in any error reporting, use canonical path for the link.
        File canonicalLink = link.canonicalFile

        if (FileHelper.isEmptyDirectory(canonicalLink)) {
            // Delete the existing "link" if it's actually an empty directory, because it may be a left-over from a
            // previous failed attempt to delete a directory junction.  This has been observed occasionally and we
            // suspect it's due to some background process temporarily having a lock on the folder -- for example, a
            // virus scanner, or the Windows Indexing Service.
            canonicalLink.delete()
        } else if (isSymlink(canonicalLink)) {
            canonicalLink.delete()
        } else if (canonicalLink.exists()) {
            throw new RuntimeException(
                "Cannot not delete or create a symlink at '${canonicalLink.path}' " +
                "because a folder or file already exists there and is not a symlink or an empty folder."
            )
        }
    }
    
    public static void rebuild(File link, File target) {
        // For simplicity in any error reporting, use canonical path for the link.
        File canonicalLink = link.canonicalFile

        // Delete the symlink if it exists
        delete(canonicalLink)
        
        // Make sure [link]'s parent directory exists
        final File linkParentDir = canonicalLink.parentFile
        FileHelper.ensureMkdirs(linkParentDir, "for symlink ${canonicalLink.name}")

        File canonicalTarget = getCanonicalTarget(canonicalLink, target)
        if (!canonicalTarget.exists()) {
            throw new IOException("Cannot create link to non-existent target: from '${canonicalLink}' to '${target}'")
        }

        File effectiveTarget = getEffectiveTarget(canonicalLink, canonicalTarget)
        Files.createSymbolicLink(canonicalLink.toPath(), effectiveTarget.toPath())
    }

    private static File getCanonicalTarget(File canonicalLink, File target) {
        if (target.absolute) {
            target
        } else {
            // If [target] is relative, we want createSymbolicLink to create a link relative to [link] (as opposed to
            // relative to the current working directory) so we have to calculate this.
            new File(canonicalLink.parentFile, target.path).canonicalFile
        }
    }

    private static File getEffectiveTarget(File canonicalLink, File canonicalTarget) {
        if (canonicalTarget.absolute) {
            canonicalTarget
        } else {
            // If [target] is relative, we want createSymbolicLink to create a link relative to [link] (as opposed to
            // relative to the current working directory) so we have to calculate this.
            canonicalLink.parentFile.toPath().relativize(canonicalTarget.toPath()).toFile()
        }
    }

    public static File getTarget(File link) {
        return Files.readSymbolicLink(link.toPath()).toFile()
    }
}
package holygradle.io

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

object Symlink {
    @JvmStatic
    fun isSymlink(file: File): Boolean = Files.isSymbolicLink(Paths.get(file.path))

    @JvmStatic
    fun delete(link: File) {
        // For simplicity in any error reporting, use canonical path for the link.
        val canonicalLink = link.canonicalFile

        when {
            FileHelper.isEmptyDirectory(canonicalLink) ->
                // Delete the existing "link" if it's actually an empty directory, because it may be a left-over from a
                // previous failed attempt to delete a directory junction.  This has been observed occasionally and we
                // suspect it's due to some background process temporarily having a lock on the folder -- for example, a
                // virus scanner, or the Windows Indexing Service.
                canonicalLink.delete()
            isSymlink(canonicalLink) -> canonicalLink.delete()
            canonicalLink.exists() -> throw RuntimeException(
                "Cannot not delete or create a symlink at '${canonicalLink.path}' " +
                "because a folder or file already exists there and is not a symlink or an empty folder."
            )
        }
    }

    @JvmStatic
    fun rebuild(link: File, target: File) {
        // For simplicity in any error reporting, use canonical path for the link.
        val canonicalLink = link.canonicalFile

        // Delete the symlink if it exists
        delete(canonicalLink)
        
        // Make sure [link]'s parent directory exists
        val linkParentDir = canonicalLink.parentFile
        FileHelper.ensureMkdirs(linkParentDir, "for symlink ${canonicalLink.name}")

        val canonicalTarget = getCanonicalTarget(canonicalLink, target)
        if (!canonicalTarget.exists()) {
            throw IOException("Cannot create link to non-existent target: from '${canonicalLink}' to '${target}'")
        }

        val effectiveTarget = getEffectiveTarget(canonicalLink, canonicalTarget)
        Files.createSymbolicLink(canonicalLink.toPath(), effectiveTarget.toPath())
    }

    private fun getCanonicalTarget(canonicalLink: File, target: File): File =
        if (target.isAbsolute) {
            target
        } else {
            // If [target] is relative, we want createSymbolicLink to create a link relative to [link] (as opposed to
            // relative to the current working directory) so we have to calculate this.
            File(canonicalLink.parentFile, target.path).canonicalFile
        }

    private fun getEffectiveTarget(canonicalLink: File, canonicalTarget: File): File =
        if (canonicalTarget.isAbsolute) {
            canonicalTarget
        } else {
            // If [target] is relative, we want createSymbolicLink to create a link relative to [link] (as opposed to
            // relative to the current working directory) so we have to calculate this.
            canonicalLink.parentFile.toPath().relativize(canonicalTarget.toPath()).toFile()
        }

    @JvmStatic
    fun getTarget(link: File): File {
        return Files.readSymbolicLink(link.toPath()).toFile()
    }
}
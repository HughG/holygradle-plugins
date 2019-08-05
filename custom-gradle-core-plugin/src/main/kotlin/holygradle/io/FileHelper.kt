package holygradle.io

import holygradle.custom_gradle.util.RetryHelper
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Utility methods related to files.
 */
object FileHelper {
    @JvmStatic
    fun isEmptyDirectory(file: File): Boolean = file.isDirectory && file.list().isEmpty()

    @JvmStatic
    @JvmOverloads
    fun ensureDeleteFile(file: File, purpose: String? = null) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            throw IOException("Failed to delete ${file}${formatPurpose(purpose)} because it is a directory, not a file")
        }
        try {
            file.setWritable(true)
            Files.delete(file.toPath())
        } catch (e: IOException) {
            throw IOException("Failed to delete ${file}${formatPurpose(purpose)}", e)
        }
        ensureDeleted(file, purpose)
    }

    @JvmStatic
    @JvmOverloads
    fun ensureDeleteDirRecursive(dir: File, purpose: String? = null) {
        if (!dir.exists()) {
            return
        }
        // Note: I tried using both the Groovy deleteDirs and the commons-io forceDelete, but neither of those could
        // cope with paths greater than about 255 in length.  The following does work with those, though.
        try {
            dir.walkTopDown().onLeave { d ->
                // If this dir was a *symlink* to a directory, then then main traverse closure will have deleted it.
                // If it still exists, we delete it now.
                if (d.exists()) {
                    d.setWritable(true)
                    RetryHelper.retry(10, 1000, null, "delete ${dir}${formatPurpose(purpose)}") {
                        Files.delete(d.toPath())
                    }
                }
            }.forEach { f ->
                if (Link.isLink(f) || !f.isDirectory) {
                    f.setWritable(true)
                    RetryHelper.retry(10, 1000, null, "delete ${dir}${formatPurpose(purpose)}") {
                        Files.delete(f.toPath())
                    }
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to delete ${dir}${formatPurpose(purpose)}", e)
        }
        ensureDeleted(dir, purpose)
    }

    /**
     * Try to wait until the file is really deleted, because sometimes there's an asynchronous delay.
     */
    private fun ensureDeleted(file: File, purpose: String? = null) {
        RetryHelper.retry(10, 1000, null, "delete ${file}${formatPurpose(purpose)}") {
            if (file.exists()) {
                throw IOException("Failed to delete ${file}${formatPurpose(purpose)}")
           }
       }
    }

    @JvmStatic
    @JvmOverloads
    fun ensureMkdir(dir: File, purpose: String? = null) {
        if (!dir.parentFile.exists()) {
            throw IOException("Failed to make ${dir}${formatPurpose(purpose)} because the parent directory does not exist")
        }
        ensureMkdirs(dir, purpose)
    }

    @JvmStatic
    @JvmOverloads
    fun ensureMkdirs(dir: File, purpose: String? = null) {
        try {
            Files.createDirectories(dir.toPath())
        } catch (e: IOException) {
            throw IOException("Failed to make ${dir}${formatPurpose(purpose)}", e)
        }
    }

    @JvmStatic
    fun setReadOnlyRecursively(root: File) {
        for (file in root.walk()) {
            if (file.isFile) {
                file.setReadOnly()
            }
        }
    }

    private fun formatPurpose(purpose: String?): String = "${if (purpose != null) " " else  ""}${purpose ?: ""}"
}

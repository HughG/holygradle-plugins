package holygradle.io

import groovy.io.FileVisitResult
import holygradle.custom_gradle.util.RetryHelper

import java.nio.file.Files

/**
 * Utility methods related to files.
 */
class FileHelper {
    public static boolean isEmptyDirectory(File file) {
        return file.isDirectory() && file.list().length == 0
    }

    public static void ensureDeleteFile(File file, String purpose = null) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory()) {
            throw new IOException("Failed to delete ${file}${formatPurpose(purpose)} because it is a directory, not a file")
        }
        try {
            file.writable = true
            Files.delete(file.toPath())
        } catch (IOException e) {
            throw new IOException("Failed to delete ${file}${formatPurpose(purpose)}", e)
        }
        ensureDeleted(file, purpose)
    }

    public static void ensureDeleteDirRecursive(File dir, String purpose = null) {
        if (!dir.exists()) {
            return
        }
        // Note: I tried using both the Groovy deleteDirs and the commons-io forceDelete, but neither of those could
        // cope with paths greater than about 255 in length.  The following does work with those, though.
        try {
            dir.traverse(
                postDir: { File f ->
                    // If this dir was a *symlink* to a directory, then then main traverse closure will have deleted it.
                    // If it still exists, we delete it now.
                    if (f.exists()) {
                        f.writable = true
                        RetryHelper.retry(10, 1000, null, "delete ${dir}${formatPurpose(purpose)}") {
                            Files.delete(f.toPath())
                        }
                    }
                    return FileVisitResult.CONTINUE
                },
                postRoot: true,
                visitRoot: true,
            ) { File f ->
                if (Link.isLink(f) || !f.isDirectory()) {
                    f.writable = true
                    RetryHelper.retry(10, 1000, null, "delete ${dir}${formatPurpose(purpose)}") {
                        Files.delete(f.toPath())
                    }
                }
                return FileVisitResult.CONTINUE
            }
        } catch (IOException e) {
            throw new IOException("Failed to delete ${dir}${formatPurpose(purpose)}", e)
        }
        ensureDeleted(dir, purpose)
    }

    // Try to wait until the file is really deleted, because sometimes there's an asynchronous delay.
    private static void ensureDeleted(File file, String purpose) {
        RetryHelper.retry(10, 1000, null, "delete ${file}${formatPurpose(purpose)}") {
            if (file.exists()) {
                throw new IOException("Failed to delete ${file}${formatPurpose(purpose)}")
            }
        }
    }

    public static void ensureMkdir(File dir, String purpose = null) {
        if (!dir.parentFile.exists()) {
            throw new IOException("Failed to make ${dir}${formatPurpose(purpose)} because the parent directory does not exist")
        }
        ensureMkdirs(dir, purpose)
    }

    public static void ensureMkdirs(File dir, String purpose = null) {
        try {
            Files.createDirectories(dir.toPath())
        } catch (IOException e) {
            throw new IOException("Failed to make ${dir}${formatPurpose(purpose)}", e)
        }
    }

    private static String formatPurpose(String purpose) {
        "${purpose ? ' ' : ''}${purpose ?: ''}"
    }
}

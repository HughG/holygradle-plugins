package holygradle.io

import groovy.io.FileVisitResult
import holygradle.custom_gradle.util.Symlink

import java.nio.file.Files

/**
 * Utility methods related to files.
 */
class FileHelper {
    public static void ensureDeleteFile(File file, String purpose = null) {
        if (!file.exists()) {
            return
        }
        // Check it's not a directory, because Commons IO method does a recursive delete in that case.
        if (file.isDirectory()) {
            throw new IOException("Failed to delete ${file}${formatPurpose(purpose)} because it is a directory, not a file")
        }
        try {
            file.writable = true
            Files.delete(file.toPath())
        } catch (IOException e) {
            throw new IOException("Failed to delete ${file}${formatPurpose(purpose)}", e)
        }
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
                        Files.delete(f.toPath())
                    }
                    return FileVisitResult.CONTINUE
                },
                postRoot: true,
                visitRoot: true,
            ) { File f ->
                if (Symlink.isJunctionOrSymlink(f) || !f.isDirectory()) {
                    f.writable = true
                    Files.delete(f.toPath())
                }
                return FileVisitResult.CONTINUE
            }
        } catch (IOException e) {
            throw new IOException("Failed to delete ${dir}${formatPurpose(purpose)}", e)
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
            throw new IOException("Failed to delete ${dir}${formatPurpose(purpose)}", e)
        }
    }

    private static String formatPurpose(String purpose) {
        "${purpose ? ' ' : ''}${purpose ?: ''}"
    }
}

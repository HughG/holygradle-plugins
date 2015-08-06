package holygradle.io

/**
 * Utility methods related to files.
 */
class FileHelper {
    public static void ensureDeleteFile(File file, String purpose = null) {
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete ${file}${formatPurpose(purpose)}")
        }
    }

    public static void ensureDeleteDirRecursive(File dir, String purpose = null) {
        // Note: File#delete() doesn't delete non-empty directory, but Groovy deleteDir extension method does.
        if (dir.exists() && !dir.deleteDir()) {
            throw new IOException("Failed to delete ${dir}${formatPurpose(purpose)}")
        }
    }

    public static void ensureMkdir(File dir, String purpose = null) {
        if (!dir.exists() && !dir.mkdir()) {
            throw new IOException("Failed to make ${dir}${formatPurpose(purpose)}")
        }
    }

    public static void ensureMkdirs(File dir, String purpose = null) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to make ${dir}${formatPurpose(purpose)}")
        }
    }

    private static String formatPurpose(String purpose) {
        "${purpose ? ' ' : ''}${purpose ?: ''}"
    }
}

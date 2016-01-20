package holygradle.io

import java.nio.file.Files

/**
 * Utility class for abstracting Symlinks and Directory Junctions
 */
class Link {
    public static boolean isLink(File link) {
        if (Files.isSymbolicLink(link.toPath()) ||
            Junction.isJunction(link)
        ) {
            return true
        }

        return false
    }

    public static void delete(File link) {
        linkTypeHelper(
            link,
            { File l -> Symlink.delete(l) },
            { File l -> Junction.delete(l) }
        )
    }

    public static void rebuild(File link, File target) {
        // Delete the link first in case it already exists as the wrong type
        delete(link)

        // Try to build as a directory junction first
        Exception junctionError
        try {
            Junction.rebuild(link, target)
            return
        } catch (Exception e) {
            junctionError = e
        }

        // If that fails, fall back to symlinks
        Exception symlinkError
        try {
            Symlink.rebuild(link, target)
            return
        } catch (Exception e) {
            symlinkError = e
        }

        throw new RuntimeException("Directory Junction creation failed with error ${junctionError.message}.\r\n" +
                                   "Symlink creation failed with error ${symlinkError.message}.")
    }

    public static File getTarget(File link) {
        File result = null
        linkTypeHelper(
            link,
            { File l -> result = Files.readSymbolicLink(l) },
            { File l -> result = Junction.getTarget(l.toPath()) }
        )
        return result
    }

    private static void linkTypeHelper(File link, Closure<File> symlinkAction, Closure<File> junctionAction) {
        if (Files.isSymbolicLink(link.toPath())) {
            symlinkAction(link)
        } else if (Junction.isJunction(link)) {
            junctionAction(link)
        }
    }
}

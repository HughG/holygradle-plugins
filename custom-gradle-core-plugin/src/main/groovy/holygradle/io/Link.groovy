package holygradle.io

import java.nio.file.Files
import org.gradle.api.logging.Logger

/**
 * Utility class for abstracting Symlinks and Directory Junctions
 */
class Link {
    public static boolean isLink(File link) {
        return Files.isSymbolicLink(link.toPath()) ||
            Junction.isJunction(link)
    }

    public static void delete(File link) {
        if (link.exists()) {
            linkTypeHelper(link, Symlink.&delete, Junction.&delete)
        }
    }

    public static void rebuild(File link, File target, Logger logger) {
        // Delete the link first in case it already exists as the wrong type.  The Junction.rebuild and Symlink.rebuild
        // methods will also do this check themselves in case they are called directly, but we need to do it here also
        // in case we are deleting a symlink to replace it with a directory junction, or vice versa.
        if (isLink(link)) {
            delete(link)
        }

        try {
            Junction.rebuild(link, target)
        } catch (Exception e) {
            logger?.debug("Failed to create a directory junction from '${link}' to '${target}'. Falling back to symlinks.", e)
            rebuildAsSymlink(link, target, logger)
        }
    }


    private static void rebuildAsSymlink(File link, File target, Logger logger) {
        // If that fails, fall back to symlinks
        try {
            Symlink.rebuild(link, target)
        } catch (Exception e2) {
            logger?.error("Directory junction and symlink creation failed from '${link}' to '${target}'.", e2)
            throw e2
        }
    }

    public static File getTarget(File link) {
        return linkTypeHelper(link, Symlink.&getTarget, Junction.&getTarget)
    }

    private static <T> T linkTypeHelper(File link, Closure<T> symlinkAction, Closure<T> junctionAction) {
        if (Files.isSymbolicLink(link.toPath())) {
            return symlinkAction(link)
        } else if (Junction.isJunction(link)) {
            return junctionAction(link)
        } else {
            throw new RuntimeException("'${link}' is not a symlink or a directory junction.")
        }
    }
}

package holygradle.io

import holygradle.custom_gradle.util.RetryHelper

import java.nio.file.Files
import org.gradle.api.logging.Logging
import org.gradle.api.logging.Logger

/**
 * Utility class for abstracting Symlinks and Directory Junctions
 */
class Link {
    private static final Logger LOGGER = Logging.getLogger(Link.class)

    public static boolean isLink(File link) {
        return Files.isSymbolicLink(link.toPath()) ||
            Junction.isJunction(link)
    }

    public static void delete(File link) {
        RetryHelper.retry(10, 1000, LOGGER, "delete link at ${link}") {
            if (link.exists()) {
                LOGGER.debug("Deleting link at ${link}")
                linkTypeHelper(link, Symlink.&delete, Junction.&delete)
            } else {
                LOGGER.debug("Skipping delete link because there is no file at ${link}")
            }
        }
    }

    public static void rebuild(File link, File target) {
        RetryHelper.retry(10, 1000, LOGGER, "rebuild link at ${link}") {
            // Delete the link first in case it already exists as the wrong type.  The Junction.rebuild and Symlink.rebuild
            // methods will also do this check themselves in case they are called directly, but we need to do it here also
            // in case we are deleting a symlink to replace it with a directory junction, or vice versa.
            if (isLink(link)) {
                delete(link)
            }

            try {
                LOGGER.debug("Creating link from ${link} to ${target}")
                Junction.rebuild(link, target)
            } catch (Exception e) {
                LOGGER.debug(
                    "Failed to create a directory junction from '${link}' to '${target}'. Falling back to symlinks.",
                    e
                )
                rebuildAsSymlink(link, target)
            }
        }
    }


    private static void rebuildAsSymlink(File link, File target) {
        // If that fails, fall back to symlinks
        try {
            Symlink.rebuild(link, target)
        } catch (Exception e) {
            LOGGER.error("Directory junction and symlink creation failed from '${link}' to '${target}'.", e)
            throw e
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

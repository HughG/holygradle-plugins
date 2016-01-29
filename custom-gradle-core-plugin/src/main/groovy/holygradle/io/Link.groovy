package holygradle.io

import java.nio.file.Files
import org.gradle.api.logging.Logger

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
        if (link.exists()) {
            linkTypeHelper(
                link,
                { File l -> Symlink.delete(l) },
                { File l -> Junction.delete(l) }
            )
        }
    }

    public static void rebuild(File link, File target, Logger logger) {
        // Delete the link first in case it already exists as the wrong type
        if (isLink(link)) {
            delete(link)
        }

        // Try to build as a directory junction first
        try {
            Junction.rebuild(link, target)
        } catch (Exception e) {
            logger?.debug("Failed to create a directory junction from ${link} to ${target}. Falling back to symlinks.", e)

            // If that fails, fall back to symlinks
            try {
                Symlink.rebuild(link, target)
            } catch (Exception e2) {
                logger?.error("Directory junction and symlink creation failed.")
                throw e2
            }
        }
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
        } else {
            throw new RuntimeException("${link} is not a symlink or a directory junction.")
        }
    }
}

package holygradle.io

import java.nio.file.Files
import org.gradle.api.logging.Logging
import java.io.File

/**
 * Utility class for abstracting Symlinks and Directory Junctions
 */
object Link {
    private val LOGGER = Logging.getLogger(Link.javaClass)

    fun isLink(link: File): Boolean {
        return Files.isSymbolicLink(link.toPath()) ||
            Junction.isJunction(link)
    }

    fun delete(link: File) {
        if (link.exists()) {
            linkTypeHelper(link, Symlink::delete, Junction::delete)
        }
    }

    fun rebuild(link: File, target: File) {
        // Delete the link first in case it already exists as the wrong type.  The Junction.rebuild and Symlink.rebuild
        // methods will also do this check themselves in case they are called directly, but we need to do it here also
        // in case we are deleting a symlink to replace it with a directory junction, or vice versa.
        if (isLink(link)) {
            delete(link)
        }

        try {
            Junction.rebuild(link, target)
        } catch (e: Exception) {
            LOGGER.debug("Failed to create a directory junction from '${link}' to '${target}'. Falling back to symlinks.", e)
            rebuildAsSymlink(link, target)
        }
    }


    private fun rebuildAsSymlink(link: File, target: File) {
        // If that fails, fall back to symlinks
        try {
            Symlink.rebuild(link, target)
        } catch (e: Exception) {
            LOGGER.error("Directory junction and symlink creation failed from '${link}' to '${target}'.", e)
            throw e
        }
    }

    fun getTarget(link: File): File {
        return linkTypeHelper(link, Symlink::getTarget, Junction::getTarget)
    }

    private fun <T> linkTypeHelper(link: File, symlinkAction: (File) -> T, junctionAction: (File) -> T): T {
        return when {
            Files.isSymbolicLink(link.toPath()) -> symlinkAction(link)
            Junction.isJunction(link) -> junctionAction(link)
            else -> throw RuntimeException("'${link}' is not a symlink or a directory junction.")
        }
    }
}

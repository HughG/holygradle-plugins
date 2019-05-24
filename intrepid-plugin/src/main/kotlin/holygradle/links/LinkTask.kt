package holygradle.links

import holygradle.io.Link
import org.gradle.api.DefaultTask
import java.io.File

open class LinkTask : DefaultTask() {
    companion object {
        /**
         * Throws an exception if {@code link} exists and is not a symlink.
         * @param link The potential link to check.
         * @param target The intended target for the link (for use in error message).
         */
        fun checkIsLinkOrMissing(link: File, target: File) {
            val linkExists = link.exists()
            val isLink = Link.isLink(link)
            if (linkExists && !isLink) {
                throw RuntimeException(
                        "Could not create a link from '${link.path}' to '${target.path}' " +
                                "because the former already exists and is not a link."
                        )
            }
        }
    }

    private val entries = mutableMapOf<File, File>()

    fun initialize() {
        doFirst {
            for ((linkDir, targetDir) in entries) {
                Link.rebuild(linkDir, targetDir)
            }
        }
    }

    fun addLink(linkDir: File, targetDir: File) {
        val existingTargetDir = entries[linkDir]
        if (existingTargetDir != null) {
            throw RuntimeException(
                "Cannot initialize for link from '${linkDir.path}' to '${targetDir.path}' " +
                "because a link has already been added from there to '${existingTargetDir.path}'"
            )
        }
        entries[linkDir] = targetDir
    }
}

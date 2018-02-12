package holygradle.links

import holygradle.io.Link
import org.gradle.api.DefaultTask
import java.io.File

class LinkTask : DefaultTask() {
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

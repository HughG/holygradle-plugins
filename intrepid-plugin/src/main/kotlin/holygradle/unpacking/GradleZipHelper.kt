package holygradle.unpacking

import org.gradle.api.Project
import java.io.File

class GradleZipHelper(private val project: Project) : Unzipper {
    override val dependencies: Any = listOf<Any>()

    override fun unzip(zipFile: File, targetDirectory: File) {
        val zipFileLastModified = zipFile.lastModified()
        project.copy { it ->
            it.from(project.zipTree(zipFile))
            it.into(targetDirectory)
            it.eachFile { details ->
                val output = File(targetDirectory, details.path)
                // If the output file already exists:
                //   - if it's newer, don't bother unzipping;
                //   - if not, make sure it's writable so we can replace it.
                if (output.exists()) {
                    if (output.lastModified() >= zipFileLastModified) {
                        details.exclude()
                    } else {
                        output.setWritable(true)
                    }
                }
            }
        }
    }
}

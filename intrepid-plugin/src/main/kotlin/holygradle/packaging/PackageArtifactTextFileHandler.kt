package holygradle.packaging

import java.io.File

interface PackageArtifactTextFileHandler {
    val name: String

    fun writeFile(targetFile: File)
}
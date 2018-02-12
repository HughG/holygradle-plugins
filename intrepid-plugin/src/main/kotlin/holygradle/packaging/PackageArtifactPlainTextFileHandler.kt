package holygradle.packaging

import java.io.File

class PackageArtifactPlainTextFileHandler(override val name: String) : PackageArtifactTextFileHandler {
    private val lines = mutableListOf<String>()

    fun add(text: String) {
        lines.add(text)
    }
  
    override fun writeFile(targetFile: File) {
        targetFile.printWriter().use {
            for (line in lines) {
                it.println(line)
            }
        }
    }
}
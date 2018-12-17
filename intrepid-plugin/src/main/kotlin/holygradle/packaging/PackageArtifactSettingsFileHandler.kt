package holygradle.packaging

import holygradle.SettingsFileHelper
import java.io.File

open class PackageArtifactSettingsFileHandler(override val name: String) : PackageArtifactTextFileHandler {
    private val includeModules = mutableListOf<String>()

    fun include(vararg modules: String) {
        includeModules.addAll(modules)
    }

    override fun writeFile(targetFile: File) {
        SettingsFileHelper.writeSettingsFile(targetFile, includeModules)
    }
}
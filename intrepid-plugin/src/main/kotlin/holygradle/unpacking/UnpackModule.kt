package holygradle.unpacking

import org.gradle.api.artifacts.ModuleVersionIdentifier

data class UnpackModule(
        val group: String,
        val name: String
) {
    val versions = mutableMapOf<String, UnpackModuleVersion>()

    fun matches(moduleVersion: ModuleVersionIdentifier): Boolean =
            (moduleVersion.group == group && moduleVersion.name == name)

    fun getVersion(moduleVersion: ModuleVersionIdentifier): UnpackModuleVersion? {
        if (matches(moduleVersion)) {
            return versions[moduleVersion.version]
        }
        return null
    }
}
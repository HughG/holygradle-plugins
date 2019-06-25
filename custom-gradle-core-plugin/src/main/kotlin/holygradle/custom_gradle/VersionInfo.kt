package holygradle.custom_gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import java.io.ByteArrayOutputStream
import java.io.File

open class VersionInfo(private val project: Project) {
    private val mutableVersions: MutableMap<String, String> = linkedMapOf()

    companion object {
        @JvmStatic
        fun defineExtension(project: Project): VersionInfo {
            return when (project) {
                project.rootProject -> project.extensions.create("versionInfo", VersionInfo::class.java, project)
                else -> {
                    val rootProjectExtension = project.rootProject.extensions.findByName("versionInfo") as VersionInfo
                    project.extensions.add("versionInfo", rootProjectExtension)
                    rootProjectExtension
                }
            }
        }
    }

    init {
        specify("gradle", project.gradle.gradleVersion)
        if (project.hasProperty("holyGradleInitScriptVersion")) {
            specify("custom Gradle init script", project.property("holyGradleInitScriptVersion") as String)
        }
    }
    
    fun specify(item: String, version: String) {
        mutableVersions[item] = version
    }
    
    fun specifyPowerShell(item: String, powerShellCommand: String) {
        val powerShellOutput = ByteArrayOutputStream()
        project.exec {
            it.commandLine("powershell", "-Command", powerShellCommand)
            it.standardOutput = powerShellOutput
            it.errorOutput = ByteArrayOutputStream()
            it.isIgnoreExitValue = true
        }
        specify(item, powerShellOutput.toString().trim())
    }
    // This returns a map from the resolved mutableVersions of all Gradle build script dependencies to the mutableVersions originally
    // requested in the "gplugins" block (which is a custom-gradle convention), or "none" if they were transitive
    // dependencies not explicitly requested or were pulled in in some other way.
    val buildscriptDependencies: Map<out ModuleVersionIdentifier, String> by lazy {
        val pluginUsagesExtension = project.extensions.findByName("pluginUsages") as PluginUsages
        pluginUsagesExtension.mapping.entries.associate { (moduleName, versions) ->
            DefaultModuleVersionIdentifier.newId("holygradle", moduleName, versions.selected) to versions.requested
        }
    }
    
    fun getVersion(plugin: String): String? = mutableVersions[plugin]

    val versions: Map<String, String> = mutableVersions

    private val windowsUpdates: String by lazy {
        val wmicOutput = ByteArrayOutputStream()
        project.exec {
            it.commandLine("wmic", "qfe", "list")
            it.standardOutput = wmicOutput
            it.errorOutput = ByteArrayOutputStream()
            it.isIgnoreExitValue = true
        }
        wmicOutput.toString()
    }
    
    fun writeFile(file: File) {
        // We avoid calling specifyPowerShell in the constructor because that will run on every build where this plugin
        // is used, adding a few seconds to startup time.  Calling it here only runs it when it's needed.
        specifyPowerShell("Windows", "[System.Environment]::OSVersion.VersionString.Trim()")

        val str = StringBuilder()
        
        str.append("Versions\n")
        str.append("========\n")
        versions.forEach { (item, version) ->
            str.append("${item} : ${version}\n")
        }
        buildscriptDependencies.forEach { (version, requestedVersionStr) ->
            str.append("${version.name} : ${version.version} (requested: $requestedVersionStr)\n")
        }
        str.append("\n")
        
        val updates = windowsUpdates
        str.append("Windows Updates\n")
        str.append("===============\n")
        str.append(updates)
        str.append("\n")

        file.writeText(str.toString())
    }
}

package holygradle.custom_gradle

import holygradle.kotlin.dsl.get
import holygradle.kotlin.dsl.withType
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolvedDependency

open class PluginUsages(project: Project) {
    class Versions(val requested: String, val selected: String)

    private val usages: Map<String, Versions>
    
    init {
        // NOTE 2017-07-28 HughG: Strictly speaking this should find the correspondence between the requested and
        // selected versions by using a DependencyResolutionListener.  This is required because a resolutionStrategy can
        // completely change the module group and name, as well as version.  However, we don't expect anyone to do that
        // with the Holy Gradle, and this approach (assuming the group and name stay the same) is easier.
        val pluginVersions: MutableMap<String, Versions> = linkedMapOf()
        val classpathConfiguration = project.buildscript.configurations["classpath"]
        val requestedHolyGradleDependencies: List<ModuleDependency> =
                classpathConfiguration.allDependencies.withType<ModuleDependency>().filter { it.group == "holygradle" }
        val resolvedHolyGradleDependencies: List<ResolvedDependency> =
                classpathConfiguration.resolvedConfiguration.firstLevelModuleDependencies.filter { it.moduleGroup == "holygradle" }
        resolvedHolyGradleDependencies.forEach { dep ->
            val requestedVersion = requestedHolyGradleDependencies.find {
                it.name == dep.moduleName
            }!!.version!!
            pluginVersions[dep.moduleName] = Versions(requestedVersion, dep.moduleVersion)
        }
        usages = pluginVersions
    }
    
    val mapping: Map<String, Versions> = usages
}

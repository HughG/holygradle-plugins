package holygradle.dependencies

import holygradle.Helper
import holygradle.artifacts.ConfigurationSet
import holygradle.artifacts.ConfigurationSetType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.io.File

abstract class DependencyHandler(
        /**
         * The path to the folder in which the dependency is to appear, relative to the containing project, as given by
         * {@link #project}.
         */
        val name: String,

        /**
         * The {@Link Project} containing this dependency.
         */
        val project: Project
) {
    /**
     * The name of the folder in which the dependency is to appear; that is, the past part of {@link #name}.
     */
    val targetName: String = File(name).name

    val configurationMappings: MutableCollection<Map.Entry<String, String>> = mutableListOf()

    val fullTargetPath: String = name

    val fullTargetPathRelativeToRootProject: String =
        Helper.relativizePath(File(project.projectDir, name), project.rootProject.projectDir)

    val absolutePath = File(project.projectDir, name)

    abstract fun configuration(config: String)

    fun configuration(configs: Iterable<String>) {
        for (it in configs) {
            configuration(it)
        }
    }

    fun configuration(vararg configs: String) {
        for (it in configs) {
            configuration(it)
        }
    }

    fun configurationSet(attrs: Map<String, Any>, source: ConfigurationSet, target: ConfigurationSet) {
        configuration(source.type.getMappingsTo(attrs, source, target))
    }

    fun configurationSet(attrs: Map<String, Any>, source: ConfigurationSet, targetType: ConfigurationSetType) {
        configuration(source.type.getMappingsTo(attrs, source, targetType))
    }

    fun configurationSet(source: ConfigurationSet, target: ConfigurationSet) {
        configurationSet(mapOf(), source, target)
    }

    fun configurationSet(source: ConfigurationSet, targetType: ConfigurationSetType) {
        configurationSet(mapOf(), source, targetType)
    }

    fun configurationSet(source: Configuration, target: ConfigurationSet) {
        configuration(target.type.getMappingsFrom(source, target))
    }

    fun configurationSet(source: Configuration, targetType: ConfigurationSetType) {
        configuration(targetType.getMappingsFrom(source))
    }
}
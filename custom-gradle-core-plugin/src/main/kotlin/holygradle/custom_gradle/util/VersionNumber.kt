package holygradle.custom_gradle.util

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

object VersionNumber {
    // Return the version number of the latest artifact with the given group and moduleName that 
    // could be found using the repositories configured for the buildscript.
    fun getLatestUsingBuildscriptRepositories(
        project: Project, group: String, moduleName: String
    ): String? {
        var latestVersion: String? = null
        val externalDependency: Dependency = DefaultExternalModuleDependency(group, moduleName, "+", "default")
        val dependencyConf: Configuration = project.buildscript.configurations.detachedConfiguration(externalDependency)
        dependencyConf.resolutionStrategy.cacheDynamicVersionsFor(1, "seconds")
        
        try {
            dependencyConf.resolvedConfiguration.firstLevelModuleDependencies.forEach {
                latestVersion = it.moduleVersion
            }
        } catch (e: Exception) {
            project.logger.info("Failed to determine latest version of '$group:$moduleName': $e", e)
        }
        
        return latestVersion
    }
}
package holygradle.custom_gradle.util

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

public class VersionNumber {
    // Return the version number of the latest artifact with the given group and moduleName that 
    // could be found using the repositories configured for the buildscript.
    public static String getLatestUsingBuildscriptRepositories(
        Project project, String group, String moduleName
    ) {
        String latestVersion = null
        def externalDependency = new DefaultExternalModuleDependency(group, moduleName, "+", "default")
        def dependencyConf = project.buildscript.configurations.detachedConfiguration(externalDependency)
        dependencyConf.resolutionStrategy.cacheDynamicVersionsFor 1, 'seconds'
        
        try {
            dependencyConf.resolvedConfiguration.firstLevelModuleDependencies.each {
                latestVersion = it.moduleVersion
            }
        } catch (Exception e) {
            project.logger.info "Failed to determine latest version of '$group:$moduleName': $e"
        }
        
        latestVersion
    }
}
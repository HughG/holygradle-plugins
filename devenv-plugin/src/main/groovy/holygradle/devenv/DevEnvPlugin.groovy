package holygradle.devenv

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*
import org.gradle.api.logging.*

class DevEnvPlugin implements Plugin<Project> {
    void apply(Project project) {
        /**************************************
         * Apply other plugins
         **************************************/
        // In normal usage we should apply the 'custom-gradle-core' plugin, but in unit tests the identifier
        // is unknown so we can't apply it. But the intrepid plugin can still largely make do without it.
        try {
            project.apply plugin: 'custom-gradle-core'
        } catch (org.gradle.api.plugins.UnknownPluginException e) {
            println "Haven't applied 'custom-gradle-core' plugin."
        }
        
        /**************************************
         * Prerequisites
         **************************************/
        def prerequisites = project.extensions.findByName("prerequisites")
        if (prerequisites != null) {
            prerequisites.register("VisualStudio", {checker, params -> 
                params.each { version ->
                    if (checker.readRegistry("HKLM\\SOFTWARE\\Microsoft\\DevDiv\\VS\\Servicing\\" + version, "SP") == null &&
                        checker.readRegistry("HKLM\\SOFTWARE\\Wow6432Node\\Microsoft\\DevDiv\\VS\\Servicing\\" + version, "SP") == null) {
                        checker.fail "Visual Studio version $version does not appear to be installed. Please install it. Detection was done by looking up the HKLM registry 'SOFTWARE\\Microsoft\\DevDiv\\VS\\Servicing\\${version}' key 'SP'."
                    }
                }
            })
        }
        
        /**************************************
         * DSL extensions
         **************************************/
        def parentDevEnvHandler = null
        if (project != project.rootProject) {
            parentDevEnvHandler = project.rootProject.extensions.findByName("DevEnv")
        }
        def devEnvHandler = project.extensions.create("DevEnv", DevEnvHandler, project, parentDevEnvHandler)  
                
        /**************************************
         * Tasks
         **************************************/
        
        def buildDebug = devEnvHandler.defineBuildTasks(project, "buildDebug", "Debug")
        def buildRelease = devEnvHandler.defineBuildTasks(project, "buildRelease", "Release")
        def cleanDebug = devEnvHandler.defineCleanTasks(project, "cleanDebug", "Debug")
        def cleanRelease = devEnvHandler.defineCleanTasks(project, "cleanRelease", "Release")
        
        project.gradle.projectsEvaluated {
            def platforms = devEnvHandler.getPlatforms()

            if (platforms.size() == 1) {
                buildDebug.each { it.configureBuildTask(devEnvHandler, platforms[0]) }
                buildRelease.each { it.configureBuildTask(devEnvHandler, platforms[0]) }
                cleanDebug.each { it.configureCleanTask(devEnvHandler, platforms[0]) }
                cleanRelease.each { it.configureCleanTask(devEnvHandler, platforms[0]) }
            } else {
                // If we have more than one platform, add specific tasks for each platform ("buildWin32Release",
                // "buildx64Release", etc.), and make the original tasks ("buildDebug") depend on all of them, as a
                // shortcut.
                platforms.each { platform ->
                    String p = platform[0].toUpperCase() + platform[1..-1]
                    def platformBuildDebugTasks = devEnvHandler.defineBuildTasks(project, "build${p}Debug", "Debug")
                    buildDebug.eachWithIndex { b, i ->
                        platformBuildDebugTasks[i].configureBuildTask(devEnvHandler, p)
                        b.dependsOn platformBuildDebugTasks[i]
                    }
                    
                    def platformBuildReleaseTasks = devEnvHandler.defineBuildTasks(project, "build${p}Release", "Release")
                    buildRelease.eachWithIndex { b, i ->
                        platformBuildReleaseTasks[i].configureBuildTask(devEnvHandler, p)
                        b.dependsOn platformBuildReleaseTasks[i]
                    }
                    
                    def platformCleanDebugTasks = devEnvHandler.defineCleanTasks(project, "clean${p}Debug", "Debug")
                    cleanDebug.eachWithIndex { b, i ->
                        platformCleanDebugTasks[i].configureCleanTask(devEnvHandler, p)
                        b.dependsOn platformCleanDebugTasks[i]
                    }
                    
                    def platformCleanReleaseTasks = devEnvHandler.defineCleanTasks(project, "clean${p}Release", "Release")
                    cleanRelease.eachWithIndex { b, i ->
                        platformCleanReleaseTasks[i].configureCleanTask(devEnvHandler, p)
                        b.dependsOn platformCleanReleaseTasks[i]
                    }
                }
                
                buildDebug.each { it.configureTaskDependencies() }
                buildRelease.each { it.configureTaskDependencies() }
                cleanDebug.each { it.configureTaskDependencies() }
                cleanRelease.each { it.configureTaskDependencies() }
            }
        }
    }
}




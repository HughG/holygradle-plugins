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
        project.apply plugin: 'custom-gradle-core'
        
        /**************************************
         * Prerequisites
         **************************************/
        def prerequisites = project.extensions.findByName("prerequisites")
        prerequisites.register("VisualStudio", {checker, params -> 
            params.each { version ->
                if (checker.readRegistry("HKLM\\SOFTWARE\\Microsoft\\DevDiv\\VS\\Servicing\\" + version, "SP") == null &&
                    checker.readRegistry("HKLM\\SOFTWARE\\Wow6432Node\\Microsoft\\DevDiv\\VS\\Servicing\\" + version, "SP") == null) {
                    checker.fail "Visual Studio version $version does not appear to be installed. Please install it. Detection was done by looking up the HKLM registry 'SOFTWARE\\Microsoft\\DevDiv\\VS\\Servicing\\${version}' key 'SP'."
                }
            }
        })
        
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
                buildDebug.each { it.configureBuildTask(devEnvHandler, platforms[0], "Debug") }
                buildRelease.each { it.configureBuildTask(devEnvHandler, platforms[0], "Release") }
                cleanDebug.each { it.configureCleanTask(devEnvHandler, platforms[0], "Debug") }
                cleanRelease.each { it.configureCleanTask(devEnvHandler, platforms[0], "Release") }
            } else {
                platforms.each { platform ->
                    String p = platform[0].toUpperCase() + platform[1..-1]
                    def platformBuildDebugTasks = devEnvHandler.defineBuildTasks(project, "build${p}Debug", "Debug")
                    buildDebug.eachWithIndex { b, i ->
                        platformBuildDebugTasks[i].configureBuildTask(devEnvHandler, p, "Debug")
                        b.dependsOn platformBuildDebugTasks[i]
                    }
                    
                    def platformBuildReleaseTasks = devEnvHandler.defineBuildTasks(project, "build${p}Release", "Release")
                    buildRelease.eachWithIndex { b, i ->
                        platformBuildReleaseTasks[i].configureBuildTask(devEnvHandler, p, "Release")
                        b.dependsOn platformBuildReleaseTasks[i]
                    }
                    
                    def platformCleanDebugTasks = devEnvHandler.defineCleanTasks(project, "clean${p}Debug", "Debug")
                    cleanDebug.eachWithIndex { b, i ->
                        platformCleanDebugTasks[i].configureCleanTask(devEnvHandler, p, "Debug")
                        b.dependsOn platformCleanDebugTasks[i]
                    }
                    
                    def platformCleanReleaseTasks = devEnvHandler.defineCleanTasks(project, "clean${p}Release", "Release")
                    cleanRelease.eachWithIndex { b, i ->
                        platformCleanReleaseTasks[i].configureCleanTask(devEnvHandler, p, "Release")
                        b.dependsOn platformCleanReleaseTasks[i]
                    }
                }
            }
        }
    }
}




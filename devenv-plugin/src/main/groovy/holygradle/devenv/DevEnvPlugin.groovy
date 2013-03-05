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
        def rebuildSymlinksTask = project.tasks.findByName("rebuildSymlinks")
        ["Debug", "Release"].each { conf ->
            project.task("build${conf}", type: DevEnvTask) {
                description = "Builds all dependent projects in $conf mode."
                if (rebuildSymlinksTask != null) {
                    dependsOn rebuildSymlinksTask
                }
            }
            project.task("build${conf}Independently", type: DevEnvTask) {
                description = "This task only makes sense for individual projects e.g. gw subproj:b${conf[0]}I"
                if (rebuildSymlinksTask != null) {
                    dependsOn rebuildSymlinksTask
                }
            }
            project.task("clean${conf}", type: DevEnvTask) {
                description = "Cleans all dependent projects in $conf mode."
            }
            project.task("clean${conf}Independently", type: DevEnvTask) {
                description = "This task only makes sense for individual projects e.g. gw subproj:c${conf[0]}I"
            }
        }
        
        project.gradle.projectsEvaluated {
            def taskDependencies = project.extensions.findByName("taskDependencies")
            if (devEnvHandler.getVsSolutionFile() != null) {
                ["Debug", "Release"].each { configuration ->
                    def buildTaskName = "build$configuration"
                    project.tasks.getByName(buildTaskName) {
                        dependsOn taskDependencies.get(buildTaskName)
                        initBuildTask(project, devEnvHandler, configuration)
                    }
                    project.tasks.getByName(buildTaskName + "Independently") {
                        initBuildTask(project, devEnvHandler, configuration)
                    }

                    def cleanTaskName = "clean$configuration"
                    project.tasks.getByName(cleanTaskName) {
                        dependsOn taskDependencies.get(cleanTaskName)
                        initCleanTask(project, devEnvHandler, configuration)
                    }
                    project.tasks.getByName(cleanTaskName + "Independently") {
                        initCleanTask(project, devEnvHandler, configuration)
                    }
                }
            }
        }
    }
}




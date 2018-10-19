package holygradle.devenv

import holygradle.IntrepidPlugin
import holygradle.custom_gradle.CustomGradleCorePlugin
import holygradle.custom_gradle.PrerequisitesExtension
import holygradle.custom_gradle.util.ProfilingHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class DevEnvPlugin implements Plugin<Project> {
    void apply(Project project) {
        ProfilingHelper profilingHelper = new ProfilingHelper(project.logger)
        def timer = profilingHelper.startBlock("DevEnvPlugin#apply(${project})")

        /**************************************
         * Apply other plugins
         **************************************/
        project.apply plugin: CustomGradleCorePlugin.class
        project.apply plugin: IntrepidPlugin.class

        /**************************************
         * Prerequisites
         **************************************/
        PrerequisitesExtension prerequisites = PrerequisitesExtension.getPrerequisites(project)
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
        DevEnvHandler parentDevEnvHandler = null
        if (project != project.rootProject) {
            parentDevEnvHandler = project.rootProject.extensions.findByName("DevEnv") as DevEnvHandler
        }
        DevEnvHandler devEnvHandler = project.extensions.create("DevEnv", DevEnvHandler, project, parentDevEnvHandler)

        /**************************************
         * Tasks
         **************************************/

        Task beforeBuild = project.task("beforeBuild") { Task it ->
            it.group = "DevEnv"
            it.description = "This task is a dependency of all build tasks, so will run before any of them"
        }
        Task beforeClean = project.task("beforeClean") { Task it ->
            it.group = "DevEnv"
            it.description = "This task is a dependency of all clean tasks, so will run before any of them"
        }

        DevEnvTask buildDebug = devEnvHandler.defineBuildTask(project, DevEnvTask.EVERY_PLATFORM, "Debug")
        DevEnvTask buildRelease = devEnvHandler.defineBuildTask(project, DevEnvTask.EVERY_PLATFORM, "Release")
        DevEnvTask cleanDebug = devEnvHandler.defineCleanTask(project, DevEnvTask.EVERY_PLATFORM, "Debug")
        DevEnvTask cleanRelease = devEnvHandler.defineCleanTask(project, DevEnvTask.EVERY_PLATFORM, "Release")

        buildDebug.dependsOn beforeBuild
        buildRelease.dependsOn beforeBuild
        cleanDebug.dependsOn beforeClean
        cleanRelease.dependsOn beforeClean

        project.gradle.projectsEvaluated {
            List<String> platforms = devEnvHandler.getPlatforms()

            if (platforms.size() == 1) {
                buildDebug.configureBuildTask(devEnvHandler, platforms[0])
                buildRelease.configureBuildTask(devEnvHandler, platforms[0])
                cleanDebug.configureCleanTask(devEnvHandler, platforms[0])
                cleanRelease.configureCleanTask(devEnvHandler, platforms[0])
            } else {
                // If we have more than one platform, add specific tasks for each platform ("buildWin32Release",
                // "buildX64Release", etc.), and make the original tasks ("buildDebug") depend on all of them, as a
                // shortcut.
                platforms.each { platform ->
                    String p = platform[0].toUpperCase() + platform[1..-1]
                    DevEnvTask platformBuildDebugTask = devEnvHandler.defineBuildTask(project, p, "Debug")
                    platformBuildDebugTask.configureBuildTask(devEnvHandler, p)
                    buildDebug.dependsOn platformBuildDebugTask
                    platformBuildDebugTask.each { it.dependsOn beforeBuild }

                    DevEnvTask platformBuildReleaseTask = devEnvHandler.defineBuildTask(project, p, "Release")
                    platformBuildReleaseTask.configureBuildTask(devEnvHandler, p)
                    buildRelease.dependsOn platformBuildReleaseTask
                    platformBuildReleaseTask.each { it.dependsOn beforeBuild }

                    DevEnvTask platformCleanDebugTask = devEnvHandler.defineCleanTask(project, p, "Debug")
                    cleanDebug.dependsOn platformCleanDebugTask
                    platformCleanDebugTask.configureCleanTask(devEnvHandler, p)
                    platformCleanDebugTask.each { it.dependsOn beforeClean }

                    DevEnvTask platformCleanReleaseTask = devEnvHandler.defineCleanTask(project, p, "Release")
                    platformCleanReleaseTask.configureCleanTask(devEnvHandler, p)
                    cleanRelease.dependsOn platformCleanReleaseTask
                    platformCleanReleaseTask.each { it.dependsOn beforeClean }
                }
            }
        }

        timer.endBlock()
    }
}




package holygradle.devenv

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
        // We should rebuild symlinks before running any build task.
        Task rebuildSymlinksTask = project.tasks.findByName("rebuildSymlinks")
        if (rebuildSymlinksTask != null) {
            beforeBuild.dependsOn rebuildSymlinksTask
        }

        List<DevEnvTask> buildDebug = devEnvHandler.defineBuildTasks(project, "buildDebug", "Debug")
        List<DevEnvTask> buildRelease = devEnvHandler.defineBuildTasks(project, "buildRelease", "Release")
        List<DevEnvTask> cleanDebug = devEnvHandler.defineCleanTasks(project, "cleanDebug", "Debug")
        List<DevEnvTask> cleanRelease = devEnvHandler.defineCleanTasks(project, "cleanRelease", "Release")

        buildDebug.each { it.dependsOn beforeBuild }
        buildRelease.each { it.dependsOn beforeBuild }
        cleanDebug.each { it.dependsOn beforeClean }
        cleanRelease.each { it.dependsOn beforeClean }

        project.gradle.projectsEvaluated {
            List<String> platforms = devEnvHandler.getPlatforms()

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
                    List<DevEnvTask> platformBuildDebugTasks = devEnvHandler.defineBuildTasks(project, "build${p}Debug", "Debug")
                    buildDebug.eachWithIndex { b, i ->
                        platformBuildDebugTasks[i].configureBuildTask(devEnvHandler, p)
                        b.dependsOn platformBuildDebugTasks[i]
                    }
                    platformBuildDebugTasks.each { it.dependsOn beforeBuild }

                    List<DevEnvTask> platformBuildReleaseTasks = devEnvHandler.defineBuildTasks(project, "build${p}Release", "Release")
                    buildRelease.eachWithIndex { b, i ->
                        platformBuildReleaseTasks[i].configureBuildTask(devEnvHandler, p)
                        b.dependsOn platformBuildReleaseTasks[i]
                    }
                    platformBuildReleaseTasks.each { it.dependsOn beforeBuild }

                    List<DevEnvTask> platformCleanDebugTasks = devEnvHandler.defineCleanTasks(project, "clean${p}Debug", "Debug")
                    cleanDebug.eachWithIndex { b, i ->
                        platformCleanDebugTasks[i].configureCleanTask(devEnvHandler, p)
                        b.dependsOn platformCleanDebugTasks[i]
                    }
                    platformCleanDebugTasks.each { it.dependsOn beforeClean }

                    List<DevEnvTask> platformCleanReleaseTasks = devEnvHandler.defineCleanTasks(project, "clean${p}Release", "Release")
                    cleanRelease.eachWithIndex { b, i ->
                        platformCleanReleaseTasks[i].configureCleanTask(devEnvHandler, p)
                        b.dependsOn platformCleanReleaseTasks[i]
                    }
                    platformCleanReleaseTasks.each { it.dependsOn beforeClean }
                }
                
                buildDebug.each { it.configureTaskDependencies() }
                buildRelease.each { it.configureTaskDependencies() }
                cleanDebug.each { it.configureTaskDependencies() }
                cleanRelease.each { it.configureTaskDependencies() }
            }
        }

        timer.endBlock()
    }
}




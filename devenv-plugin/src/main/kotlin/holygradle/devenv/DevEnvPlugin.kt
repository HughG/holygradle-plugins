package holygradle.devenv

import holygradle.IntrepidPlugin
import holygradle.custom_gradle.CustomGradleCorePlugin
import holygradle.custom_gradle.PrerequisitesChecker
import holygradle.custom_gradle.PrerequisitesExtension
import holygradle.custom_gradle.util.ProfilingHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.script.lang.kotlin.apply
import org.gradle.script.lang.kotlin.task

class DevEnvPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val profilingHelper = ProfilingHelper(project.logger)
        val timer = profilingHelper.startBlock("DevEnvPlugin#apply(${project})")

        /**************************************
         * Apply other plugins
         **************************************/
        project.apply<CustomGradleCorePlugin>()
        project.apply<IntrepidPlugin>()

        /**************************************
         * Prerequisites
         **************************************/
        val prerequisites = PrerequisitesExtension.getPrerequisites(project)
        prerequisites?.register("VisualStudio", { checker: PrerequisitesChecker<List<String>> ->
            checker.parameter.forEach { version ->
                if (checker.readRegistry("HKLM\\SOFTWARE\\Microsoft\\DevDiv\\VS\\Servicing\\" + version, "SP") == null &&
                        checker.readRegistry("HKLM\\SOFTWARE\\Wow6432Node\\Microsoft\\DevDiv\\VS\\Servicing\\" + version, "SP") == null) {
                    checker.fail("Visual Studio version $version does not appear to be installed. Please install it. Detection was done by looking up the HKLM registry 'SOFTWARE\\Microsoft\\DevDiv\\VS\\Servicing\\${version}' key 'SP'.")
                }
            }
        })

        /**************************************
         * DSL extensions
         **************************************/
        var parentDevEnvHandler: DevEnvHandler? = null
        if (project != project.rootProject) {
            parentDevEnvHandler = project.rootProject.extensions.findByName("DevEnv") as DevEnvHandler
        }
        val devEnvHandler = project.extensions.create("DevEnv", DevEnvHandler::class.java, project, parentDevEnvHandler)


        /**************************************
         * Tasks
         **************************************/

        val beforeBuild = project.task("beforeBuild") {
            group = "DevEnv"
            description = "This task is a dependency of all build tasks, so will run before any of them"
        }
        val beforeClean = project.task("beforeClean") {
            group = "DevEnv"
            description = "This task is a dependency of all clean tasks, so will run before any of them"
        }

        val buildDebug = devEnvHandler.defineBuildTask(project, DevEnvTask.EVERY_PLATFORM, "Debug")
        val buildRelease = devEnvHandler.defineBuildTask(project, DevEnvTask.EVERY_PLATFORM, "Release")
        val cleanDebug = devEnvHandler.defineCleanTask(project, DevEnvTask.EVERY_PLATFORM, "Debug")
        val cleanRelease = devEnvHandler.defineCleanTask(project, DevEnvTask.EVERY_PLATFORM, "Release")

        buildDebug.dependsOn(beforeBuild)
        buildRelease.dependsOn(beforeBuild)
        cleanDebug.dependsOn(beforeClean)
        cleanRelease.dependsOn(beforeClean)

        project.gradle.projectsEvaluated {
            val platforms = devEnvHandler.platforms

            if (platforms.size == 1) {
                buildDebug.configureBuildTask(devEnvHandler, platforms[0])
                buildRelease.configureBuildTask(devEnvHandler, platforms[0])
                cleanDebug.configureCleanTask(devEnvHandler, platforms[0])
                cleanRelease.configureCleanTask(devEnvHandler, platforms[0])
            } else {
                // If we have more than one platform, add specific tasks for each platform ("buildWin32Release",
                // "buildX64Release", etc.), and make the original tasks ("buildDebug") depend on all of them, as a
                // shortcut.
                platforms.forEach { platform ->
                    val p = platform[0].toUpperCase() + platform.substring(1)
                    val platformBuildDebugTask = devEnvHandler.defineBuildTask(project, p, "Debug")
                    platformBuildDebugTask.configureBuildTask(devEnvHandler, p)
                    buildDebug.dependsOn(platformBuildDebugTask)
                    platformBuildDebugTask.dependsOn(beforeBuild)

                    val platformBuildReleaseTask = devEnvHandler.defineBuildTask(project, p, "Release")
                    platformBuildReleaseTask.configureBuildTask(devEnvHandler, p)
                    buildRelease.dependsOn(platformBuildReleaseTask)
                    platformBuildReleaseTask.dependsOn(beforeBuild)

                    val platformCleanDebugTask = devEnvHandler.defineCleanTask(project, p, "Debug")
                    cleanDebug.dependsOn(platformCleanDebugTask)
                    platformCleanDebugTask.configureCleanTask(devEnvHandler, p)
                    platformCleanDebugTask.dependsOn(beforeClean)

                    val platformCleanReleaseTask = devEnvHandler.defineCleanTask(project, p, "Release")
                    platformCleanReleaseTask.configureCleanTask(devEnvHandler, p)
                    cleanRelease.dependsOn(platformCleanReleaseTask)
                    platformCleanReleaseTask.dependsOn(beforeClean)
                }
            }
        }

        timer.endBlock()
    }
}
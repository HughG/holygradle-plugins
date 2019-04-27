package holygradle.devenv

import holygradle.custom_gradle.plugin_apis.StampingProvider
import holygradle.logging.DefaultStyledTextOutput
import holygradle.logging.ErrorHighlightingOutputStream
import holygradle.logging.StyledTextOutput
import holygradle.source_dependencies.SourceDependenciesStateHandler
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.process.ExecSpec
import java.io.File
import java.util.regex.Pattern

/**
 * Copyright (c) 2016 Hugh Greene (githugh@tameter.org).
 */
open class DevEnvTask: DefaultTask() {
    // What operation (e.g. build/clean) are we running?
    lateinit var operation: Operation

    // What configuration (e.g. Debug/Release) are we operating on?
    lateinit var configuration: String

    // What platform (e.g. Win32/x64) are we operating on?  Any empty string means "any/all platform(s)".
    var platform = EVERY_PLATFORM

    private val output: StyledTextOutput = DefaultStyledTextOutput(System.out)

    companion object {
        internal const val EVERY_PLATFORM = ""

        /**
         * Returns the appropriate name for a {@link DevEnvTask}.
         * @param operation The kind of operation which the task performs.
         * @param platform The platform (x86, Win32), using {@link #EVERY_PLATFORM} for "any/all platform(s)".
         * @param configuration The Visual Studio configuration; must be "Debug" or "Release".
         */
        internal fun getNameForTask(operation: Operation, platform: String, configuration: String): String {
            return "${operation}${platform.capitalize()}${configuration}"
        }
    }

    enum class Operation(val prettyName: String) {
        BUILD("build"),
        CLEAN("clean");

        override fun toString(): String = prettyName
    }

    init {
        group = "DevEnv"
    }

    fun init(operation: Operation, configuration: String) {
        this.operation = operation
        this.configuration = configuration
    }

    private fun addStampingDependencyForProject(project: Project) {
        val stampingExtension = (project.extensions.findByName("stamping") ?: return) as StampingProvider
        if (stampingExtension.runPriorToBuild) {
            dependsOn(project.tasks.findByName(stampingExtension.taskName))
        }
    }

    /**
     * WARNING: Only call this inside a gradle.projectsEvaluated block.
     */
    fun configureBuildTask(devEnvHandler: DevEnvHandler, platform: String) {
        if (this.operation != Operation.BUILD) {
            throw RuntimeException("Expected task to have been initialised with 'build' operation.")
        }
        this.platform = platform

        val vsSolutionFile = devEnvHandler.vsSolutionFile
        if (vsSolutionFile != null) {
            addStampingDependencyForProject(project)
            if (project != project.rootProject) {
                addStampingDependencyForProject(project.rootProject)
            }

            doConfigureBuildTask(
                    project, devEnvHandler::getBuildToolPath, vsSolutionFile,
                    devEnvHandler.useIncredibuild, platform, configuration,
                    devEnvHandler.warningRegexes, devEnvHandler.errorRegexes
            )
        }
    }

    private fun configureTaskDependencies() {
        // Add dependencies to tasks with the same "devenv task configuration" (rather than "Gradle configuration") and
        // operation in dependent projects.
        val sourceDependenciesState =
            project.extensions.findByName("sourceDependenciesState") as? SourceDependenciesStateHandler
        if (sourceDependenciesState == null || !project.gradle.startParameter.isBuildProjectDependencies) {
            return
        }
        // For each configurations in this project which point into a source dependency, make this project's task
        // depend on the same task in the other project, and on the platform-independent task (if they exist).
        sourceDependenciesState.getAllConfigurationsPublishingSourceDependencies().forEach { config: Configuration ->
            this.dependsOn(config.getTaskDependencyFromProjectDependency(true, this.name))
            val anyPlatformTaskName = getNameForTask(this.operation, EVERY_PLATFORM, this.configuration)
            this.dependsOn(config.getTaskDependencyFromProjectDependency(true, anyPlatformTaskName))
        }
    }

    fun configureCleanTask(devEnvHandler: DevEnvHandler, platform: String) {
        if (this.operation != Operation.CLEAN) {
            throw RuntimeException("Expected task to have been initialised with 'clean' operation.")
        }
        this.platform = platform

        val vsSolutionFile = devEnvHandler.vsSolutionFile
        if (vsSolutionFile != null) {
            doConfigureCleanTask(
                    project, devEnvHandler::getBuildToolPath, vsSolutionFile,
                    platform, configuration,
                    devEnvHandler.warningRegexes, devEnvHandler.errorRegexes
            )
        }
    }

    fun doConfigureBuildTask(
            project: Project, getBuildToolPath: () -> File, solutionFile: File, useIncredibuild: Boolean,
            platform: String, configuration: String, warningRegexes: Iterable<Pattern>, errorRegexes: Iterable<Pattern>
    ) {
        val targetSwitch = if (useIncredibuild) {"/Build"} else {"/build"}
        val configSwitch = if (useIncredibuild) {"/cfg=\"${configuration}|${platform}\""} else {"${configuration}^|${platform}"}
        val buildToolName = if (useIncredibuild) {"Incredibuild"} else {"Env"}
        val outputFile = File(solutionFile.parentFile, "build_${configuration}_${platform}.txt")
        val title = "${project.name} (${platform} ${configuration})"

        description = "Builds the solution '${solutionFile.name}' with ${buildToolName} in $configuration mode, " +
                "first building all dependent projects. Run 'gw -a ...' to build for this project only."
        configureTask(
                project, title, getBuildToolPath, solutionFile, targetSwitch,
                configSwitch, outputFile, warningRegexes, errorRegexes
        )
    }

    fun doConfigureCleanTask(
        project: Project, getBuildToolPath: () -> File, solutionFile: File,
        platform: String, configuration: String, warningRegexes: Iterable<Pattern>, errorRegexes: Iterable<Pattern>
    ) {
        val targetSwitch = "/Clean"
        val configSwitch = "${configuration}^|${platform}"
        val outputFile = File(solutionFile.parentFile, "clean_${configuration}_${platform}.txt")
        val title = "${project.name} (${platform} ${configuration})"

        description = "Cleans the solution '${solutionFile.name}' with DevEnv in $configuration mode, " +
                "first building all dependent projects. Run 'gw -a ...' to build for this project only."
        configureTask(
                project, title, getBuildToolPath, solutionFile, targetSwitch,
                configSwitch, outputFile, warningRegexes, errorRegexes
        )
    }

    fun configureTask(
            project: Project, title: String, getBuildToolPath: () -> File, solutionFile: File,
            targetSwitch: String, configSwitch: String, outputFile: File,
            warningRegexes: Iterable<Pattern>, errorRegexes: Iterable<Pattern>
    ) {
        doLast {
            val devEnvOutput = ErrorHighlightingOutputStream(title, this.output, warningRegexes, errorRegexes)
            val buildToolPath = getBuildToolPath()
            val result = project.exec { spec: ExecSpec ->
                spec.workingDir(project.projectDir.path)
                spec.commandLine(buildToolPath.path, solutionFile.path, targetSwitch, configSwitch)
                spec.standardOutput = devEnvOutput
                spec.isIgnoreExitValue = true
            }

            // Summarise errors and warnings.
            devEnvOutput.summarise()

            // Write the entire output to a file.
            outputFile.writeText(devEnvOutput.toString())

            val exit = result.exitValue
            if (exit != 0) {
                throw RuntimeException("${buildToolPath.name} exited with code $exit.")
            }
        }
        configureTaskDependencies()
    }

}
package holygradle.unit_test

import holygradle.IntrepidPlugin
import holygradle.gradle.api.lazyConfiguration
import holygradle.io.FileHelper
import holygradle.source_dependencies.SourceDependenciesStateHandler
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Exec
import holygradle.kotlin.dsl.extra
import holygradle.kotlin.dsl.getByName
import holygradle.kotlin.dsl.getValue
import holygradle.kotlin.dsl.task
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*

internal open class TestHandler(
        val project: Project,
        val name: String
) {
    companion object {
        @JvmStatic
        val DEFAULT_FLAVOURS: Collection<String> = Collections.unmodifiableCollection(listOf("Debug", "Release"))

        @JvmStatic
        fun createContainer(project: Project): NamedDomainObjectContainer<TestHandler> {
            project.extensions.add("tests", project.container(TestHandler::class.java) { TestHandler(project, it) })
            return project.extensions.getByName<NamedDomainObjectContainer<TestHandler>>("tests")
        }

        private fun replaceFlavour(input: String, flavour: String): String {
            val lower = flavour.toLowerCase()
            val upper = flavour.toUpperCase()
            return input
                    .replace("<flavour>", lower)
                    .replace("<Flavour>", upper[0] + lower.substring(1))
                    .replace("<FLAVOUR>", upper)
                    .replace("<f>", lower.substring(0, 1))
                    .replace("<F>", upper.substring(0, 1))
        }

        private fun makeOutputStream(project: Project, flavour: String, target: Any): FileOutputStream {
            val outputFile = project.file(target)
            val flavouredOutputFile = File(replaceFlavour(outputFile.toString(), flavour))
            FileHelper.ensureMkdirs(flavouredOutputFile.parentFile, "as parent folder for test output")
            return FileOutputStream(flavouredOutputFile)
        }

        internal fun defineTasks(project: Project) {
            val allFlavours = TestFlavourHandler.getAllFlavours(project)
            for (flavour in allFlavours) {
                val sourceDependenciesState: SourceDependenciesStateHandler? by project.extensions
                val anyBuildDependencies =
                    (sourceDependenciesState != null) &&
                        !sourceDependenciesState.getAllConfigurationsPublishingSourceDependencies().isEmpty()

                val task: DefaultTask = project.task<DefaultTask>("unitTest${flavour}")
                task.group = "Unit Test"
                task.description = "Run the ${flavour} unit tests for '${project.name}'."

                val tests: Collection<TestHandler> by project.extensions
                for (testHandler in tests) {
                    // Create a separate task for each of the tests to allow them to be run individually
                    val subtask = project.task<Exec>("unitTest${testHandler.name.capitalize()}${flavour}")
                    testHandler.configureTask(flavour, subtask)
                    task.dependsOn(subtask)
                }

                if (anyBuildDependencies && project.gradle.startParameter.isBuildProjectDependencies) {
                    task.description =
                            "Runs the ${flavour} unit tests for '${project.name}' and all dependent projects. " +
                                    "Run 'gw -a ...' to run tests for this project only."
                    if (sourceDependenciesState != null) {
                        // For each configurations in this project which point into a source dependency, make this
                        // project's task depend on the same task in the other project (if it exists).
                        for (conf in sourceDependenciesState.getAllConfigurationsPublishingSourceDependencies()) {
                            task.dependsOn(conf.getTaskDependencyFromProjectDependency(true, task.name))
                        }
                    }
                }
            }
            if (allFlavours.size > 1) {
                val buildReleaseTask = project.tasks.findByName("buildRelease")
                val buildDebugTask = project.tasks.findByName("buildDebug")
                if (buildReleaseTask != null && buildDebugTask != null) {
                    val buildAndTestAll = project.task<DefaultTask>("buildAndTestAll") {
                        description ="Build '${project.name}' and all dependent projects in Debug and Release, and run unit tests."
                    }
                    for (flavour in allFlavours) {
                        buildAndTestAll.dependsOn("unitTest${flavour}")
                    }
                    buildAndTestAll.dependsOn(buildDebugTask, buildReleaseTask)
                }
            }
        }
    }

    private val commandLineChunks: MutableList<String> = LinkedList()
    private var standardOutputTemplate: Any? = null
    private var standardOutputTeeTemplate: Any? = null
    private var errorOutputTemplate: Any? = null
    private var errorOutputTeeTemplate: Any? = null
    var workingDir: Any? = null
    private var selectedFlavours: MutableCollection<String> = ArrayList(DEFAULT_FLAVOURS)

    @Suppress("unused") // This is an API for build scripts.
    fun commandLine(vararg cmdLine: String) {
        commandLineChunks.addAll(cmdLine)
    }

    /**
     * Sets the standard output of the test executable to the given {@code standardOutputTemplate}.  The argument is
     * interpreted as a {@link File} as for the {@link Project#file} method, and then treated as a template: any
     * "&lt;flavour&gt;" strings are replaced in the filename.  At task configuration time the parent folder of the
     * file is created if it does not already exist.
     *
     * This method is similar to {@link org.gradle.process.ExecSpec@setStandardOutput} but there is no matching
     * {@code getStandardOutput} method because there may be no single {@link OutputStream} to return: there may be one
     * such stream for each task.
     *
     * @param standardOutputTemplate An {@link Object} used as a template to set the output file for each test task.
     */
    @Suppress("unused") // This is an API for build scripts.
    fun setStandardOutput(standardOutputTemplate: Any) {
        this.standardOutputTemplate = standardOutputTemplate
    }

    /**
     * Redirects the standard output of the test executable to both Gradle's standard output and the given
     * {@code teeTemplate}.  The argument is interpreted as a {@link File} as for the {@link Project#file} method, and
     * then treated as a template: any "&lt;flavour&gt;" strings are replaced in the filename.  At task configuration
     * time the parent folder of the file is created if it does not already exist.
     *
     * @param teeTemplate An {@link Object} used as a template to set a tee output file for each test task.
     */
    @Suppress("unused") // This is an API for build scripts.
    fun setStandardOutputTee(teeTemplate: Any) {
        this.standardOutputTeeTemplate = teeTemplate
    }

    /**
     * Sets the standard error of the test executable to the given {@code standardErrorTemplate}.  The argument is
     * interpreted as a {@link File} as for the {@link Project#file} method, and then treated as a template: any
     * "&lt;flavour&gt;" strings are replaced in the filename.  At task configuration time the parent folder of the
     * file is created if it does not already exist.
     *
     * This method is similar to {@link org.gradle.process.ExecSpec@setErrorOutput} but there is no matching
     * {@code getStandardError} method because there may be no single {@link OutputStream} to return: there may be one
     * such stream for each task.
     *
     * @param standardErrorTemplate An {@link Object} used as a template to set the error output file for each test task.
     */
    @Suppress("unused") // This is an API for build scripts.
    fun setErrorOutput(standardErrorTemplate: Any) {
        this.errorOutputTemplate = standardErrorTemplate
    }

    /**
     * Redirects the standard error of the test executable to both Gradle's standard error and the given
     * {@code teeTemplate}.  The argument is interpreted as a {@link File} as for the {@link Project#file} method, and
     * then treated as a template: any "&lt;flavour&gt;" strings are replaced in the filename.  At task configuration
     * time the parent folder of the file is created if it does not already exist.
     *
     * @param teeTemplate An {@link Object} used as a template to set a tee error output file for each test task.
     */
    @Suppress("unused") // This is an API for build scripts.
    fun setErrorOutputTee(teeTemplate: Any) {
        this.errorOutputTeeTemplate = teeTemplate
    }

    @Suppress("unused") // This is an API for build scripts.
    fun workingDir(workingDir: Any?) {
        this.workingDir = workingDir
    }

    @Suppress("unused") // This is an API for build scripts.
    fun flavour(vararg newFlavours: String) {
        selectedFlavours.clear()
        selectedFlavours.addAll(newFlavours)
    }

    private fun configureTask(flavour: String, task: Exec) {
        if (selectedFlavours.contains(flavour)) {
            task.onlyIf {
                val commandLineEmpty = (commandLineChunks.isEmpty())
                if (commandLineEmpty) {
                    project.logger.warn(
                            "WARNING: Not running unit test ${name} (${flavour}) because command line is empty."
                    )
                }
                !commandLineEmpty
            }

            // ---- Set up the command line.
            val cmd = commandLineChunks.mapTo(mutableListOf()) { replaceFlavour(it, flavour) }

            // The Holy Gradle has so far tried to be "magically helpful" because some users got confused by the way it
            // finds EXEs in a multi-project build.  It would look for the EXE relative to the root project's projectDir
            // as well as the current project and, failing that, assume it was on the path.  I think the right answer is
            // to educate users about project.exec.
            val exePathString = cmd[0]
            val exePath = File(exePathString)
            val tryProjectPath = project.file(exePathString)
            if (tryProjectPath.exists()) {
                // The exe string was an absolute path or exists relative to the project dir, so we should use that.
                // By default on Windows (with the Oracle JDK), the Exec task looks in the java.exe dir, the
                // current dir of the Gradle process (or is it the root project dir?), and the path.
                cmd[0] = tryProjectPath.path
            } else if (exePath.parent == null) {
                // The exe string has no directory parts, so Exec task will find it on the system path.
            } else {
                // Note that, if the file doesn't exist at configuration time, it may just be that the test EXE hasn't
                // been built yet.
                project.logger.warn(
                        "WARNING: For test ${name} in ${project}, " +
                        "the test executable was specified as '${exePathString}' but was NOT found relative to " +
                        "that project or the root project, so may not run.  It may not be built yet."
                )
            }
            task.commandLine(cmd)

            // ---- Set up the output and/or error stream.
            configureTaskOutputStreams(flavour, task)

            // ---- Set up the workingDir.
            val workingDirLocal = workingDir
            if (workingDirLocal != null) {
                task.workingDir(workingDirLocal)
            }
        }
    }

    private fun configureTaskOutputStreams(flavour: String, task: Exec) {
        val originalStandardOutput = task.standardOutput
        val originalErrorOutput = task.errorOutput

        task.lazyConfiguration {
            val testOutputStream = when {
                standardOutputTemplate != null -> makeOutputStream(project, flavour, standardOutputTemplate!!)
                else -> standardOutput
            }
            configureOutput(
                flavour,
                this,
                "standard",
                originalStandardOutput,
                testOutputStream,
                standardOutputTeeTemplate,
                ::getStandardOutput,
                ::setStandardOutput
            )

            val testErrorStream = when {
                errorOutputTemplate != null -> makeOutputStream(project, flavour, errorOutputTemplate!!)
                else -> errorOutput
            }
            configureOutput(
                    flavour,
                    this,
                    "error",
                    originalErrorOutput,
                    testErrorStream,
                    errorOutputTeeTemplate,
                    ::getErrorOutput,
                    ::setErrorOutput
            )
        }
    }

    private fun configureOutput(
            flavour: String,
            task: Exec,
            streamType: String,
            originalOutputStream: OutputStream,
            outputStream: OutputStream,
            teeTemplate: Any?,
            getStream: () -> OutputStream,
            setStream: (OutputStream) -> AbstractExecTask<AbstractExecTask<*>>
    ) {
        val currentOutputStream = getStream()
        if (currentOutputStream != outputStream) {
            if (currentOutputStream != originalOutputStream) {
                task.logger.warn(
                        "WARNING:: Ignoring ${streamType} output stream override for ${task.name} because " +
                        "the task's output stream has already been changed from its original value"
                )
            } else {
                setStream(outputStream)
            }
        }
        if (teeTemplate != null) {
            setStream(TeeOutputStream(
                    currentOutputStream,
                    makeOutputStream(task.project, flavour, teeTemplate)
            ))
        }
    }
}
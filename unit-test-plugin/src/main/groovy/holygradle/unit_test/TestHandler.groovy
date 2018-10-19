package holygradle.unit_test

import holygradle.io.FileHelper
import holygradle.source_dependencies.SourceDependenciesStateHandler
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Exec

class TestHandler {
    public static final Collection<String> DEFAULT_FLAVOURS = Collections.unmodifiableCollection(["Debug", "Release"])

    public final Project project
    public final String name
    private Collection<String> commandLineChunks = []
    private String redirectOutputFilePath
    private Object standardOutputTemplate
    private Object standardOutputTeeTemplate
    private Object errorOutputTemplate
    private Object errorOutputTeeTemplate
    private Object workingDir
    private Collection<String> selectedFlavours = new ArrayList<String>(DEFAULT_FLAVOURS)

    public static NamedDomainObjectContainer<TestHandler> createContainer(Project project) {
        project.extensions.tests = project.container(TestHandler) { String name -> new TestHandler(project, name) }
        project.extensions.tests
    }
    
    public TestHandler(Project project, String name) {
        this.project = project
        this.name = name
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void commandLine(String... cmdLine) {
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
    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void setStandardOutput(Object standardOutputTemplate) {
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
    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void setStandardOutputTee(Object teeTemplate) {
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
    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void setErrorOutput(Object standardErrorTemplate) {
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
    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void setErrorOutputTee(Object teeTemplate) {
        this.errorOutputTeeTemplate = teeTemplate
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public Object getWorkingDir() {
        return workingDir
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void setWorkingDir(Object workingDir) {
        this.workingDir = workingDir
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void workingDir(Object workingDir) {
        this.workingDir = workingDir
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void flavour(String... newFlavours) {
        selectedFlavours.clear()
        selectedFlavours.addAll(newFlavours)
    }

    private static OutputStream makeOutputStream(Project project, String flavour, Object target) {
        final File outputFile = project.file(target)
        final File flavouredOutputFile = new File(replaceFlavour(outputFile.toString(), flavour))
        FileHelper.ensureMkdirs(flavouredOutputFile.parentFile, "as parent folder for test output")
        return new FileOutputStream(flavouredOutputFile)
    }

    private void configureTask(String flavour, Exec task) {
        if (selectedFlavours.contains(flavour)) {
            task.onlyIf {
                boolean commandLineEmpty = (commandLineChunks.size() == 0)
                if (commandLineEmpty) {
                    println "Not running unit test ${name} (${flavour}) because command line is empty."
                }
                return !commandLineEmpty
            }

            // ---- Set up the command line.
            List<String> cmd = commandLineChunks.collect { replaceFlavour(it, flavour) }

            // The Holy Gradle has so far tried to be "magically helpful" because some users got confused by the way it
            // finds EXEs in a multi-project build.  It would look for the EXE relative to the root project's projectDir
            // as well as the current project and, failing that, assume it was on the path.  I think the right answer is
            // to educate users about project.exec.
            final exePathString = cmd[0]
            File exePath = new File(exePathString)
            File tryProjectPath = project.file(exePathString)
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
                project.logger.debug(
                    "WARNING: For test ${name} in ${project}, " +
                    "the test executable was specified as '${exePathString}' but was NOT found relative to " +
                    "that project or the root project, so may not run."
                )
            }
            task.commandLine cmd

            // ---- Set up the output and/or error stream.
            configureTaskOutputStreams(flavour, task)

            // ---- Set up the workingDir.
            if (workingDir != null) {
                task.workingDir workingDir
            }
        }
    }

    private void configureTaskOutputStreams(String flavour, Exec task) {
        final OutputStream originalStandardOutput = task.standardOutput
        final OutputStream originalErrorOutput = task.errorOutput

        task.ext.lazyConfiguration = { Exec it ->
            final OutputStream testOutputStream
            if (standardOutputTemplate != null) {
                testOutputStream = makeOutputStream(project, flavour, standardOutputTemplate)
            } else if (redirectOutputFilePath != null) {
                testOutputStream = makeOutputStream(project, flavour, new File(project.projectDir, redirectOutputFilePath))
            } else {
                testOutputStream = it.standardOutput
            }
            configureOutput(
                flavour,
                it,
                "standard",
                originalStandardOutput,
                testOutputStream,
                standardOutputTeeTemplate,
                it.&getStandardOutput,
                it.&setStandardOutput
            )

            final OutputStream testErrorStream
            if (errorOutputTemplate != null) {
                testErrorStream = makeOutputStream(project, flavour, errorOutputTemplate)
            } else {
                testErrorStream = it.errorOutput
            }
            configureOutput(
                flavour,
                it,
                "error",
                originalErrorOutput,
                testErrorStream,
                errorOutputTeeTemplate,
                it.&getErrorOutput,
                it.&setErrorOutput
            )
        }
    }

    // Only public so that it can be called from a Closure.
    public static void configureOutput(
        String flavour,
        Exec task,
        String streamType,
        OutputStream originalOutputStream,
        OutputStream outputStream,
        Object teeTemplate,
        Closure<OutputStream> getStream,
        Closure setStream
    ) {
        final OutputStream currentOutputStream = getStream()
        if (currentOutputStream != outputStream) {
            if (currentOutputStream != originalOutputStream) {
                task.logger.warn(
                    "Ignoring ${streamType} output stream override for ${task.name} because the task's output stream " +
                    "has already been changed from its original value"
                )
            } else {
                setStream(outputStream)
            }
        }
        if (teeTemplate != null) {
            setStream(new TeeOutputStream(
                currentOutputStream,
                makeOutputStream(task.project, flavour, teeTemplate)
            ))
        }
    }

    public static void defineTasks(Project project) {
        Collection<String> allFlavours = TestFlavourHandler.getAllFlavours(project)
        for (flavour in allFlavours) {
            Collection<SourceDependenciesStateHandler> sourceDependenciesState =
                project.extensions.findByName("sourceDependenciesState") as
                    Collection<SourceDependenciesStateHandler>
            boolean anyBuildDependencies =
                (sourceDependenciesState != null) &&
                    !sourceDependenciesState.allConfigurationsPublishingSourceDependencies.empty

            Task task = project.task("unitTest${flavour}", type: DefaultTask)
            task.group = "Unit Test"
            task.description = "Run the ${flavour} unit tests for '${project.name}'."
                
            project.extensions.tests.each { TestHandler it ->
                // Create a separate task for each of the tests to allow them to be run individually
                Exec subtask = (Exec)project.task("unitTest${it.name.capitalize()}${flavour}", type: Exec)
                it.configureTask(flavour, subtask)
                task.dependsOn subtask
            }
            
            if (anyBuildDependencies && project.gradle.startParameter.buildProjectDependencies) {
                task.description =
                        "Runs the ${flavour} unit tests for '${project.name}' and all dependent projects. " +
                        "Run 'gw -a ...' to run tests for this project only."
                if (sourceDependenciesState != null) {
                    // For each configurations in this project which point into a source dependency, make this
                    // project's task depend on the same task in the other project (if it exists).
                    sourceDependenciesState.allConfigurationsPublishingSourceDependencies.each { Configuration conf ->
                        task.dependsOn conf.getTaskDependencyFromProjectDependency(true, task.name)
                    }
                }
            }
        }
        if (allFlavours.size() > 1) {
            Task buildReleaseTask = project.tasks.findByName("buildRelease")
            Task buildDebugTask = project.tasks.findByName("buildDebug")
            if (buildReleaseTask != null && buildDebugTask != null) {
                Task buildAndTestAll = project.task("buildAndTestAll", type: DefaultTask) {
                    description = "Build '${project.name}' and all dependent projects in Debug and Release, and run unit tests."
                }
                for (flavour in allFlavours) {
                    buildAndTestAll.dependsOn "unitTest${flavour}"
                }
                buildAndTestAll.dependsOn buildDebugTask, buildReleaseTask
            }
        }
    }
    
    private static String replaceFlavour(String input, String flavour) {
        String lower = flavour.toLowerCase()
        String upper = flavour.toUpperCase()
        input
            .replace("<flavour>", lower)
            .replace("<Flavour>", upper[0] + lower[1..-1])
            .replace("<FLAVOUR>", upper)
            .replace("<f>", lower[0])
            .replace("<F>", upper[0])
    }
}
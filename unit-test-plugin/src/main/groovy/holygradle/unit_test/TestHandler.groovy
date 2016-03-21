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
    private Object standardOutput
    private Object teeTarget
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

    @Deprecated
    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void redirectOutputToFile(String outputFilePath) {
        redirectOutputFilePath = outputFilePath
        project.logger.warn(
            "redirectOutputFilePath is deprecated. Instead, set the standardOutput property and/or the " +
            "standardOutputTee property. The standardOutput property overrides any value passed to this method. " +
            "Note that the redirectOutputFilePath value is interpreted relative to the projectDir but the " +
            "value assigned to the standardOutput property is non. If you want to redirect standardOutput to " +
            "a location relative to the projectDir, you must explicitly include projectDir in the value you set."
        )
    }

    /**
     * Sets the standard output of the test executable to the given {@code standardOutput}.  If the
     * {@code standardOutput} is an {@link OutputStream} it is used directly.  Otherwise it is interpreted as for
     * {@link Project#file(java.lang.Object)}, any "&lt;flavour&gt;" strings are replaced in the filename, the parent
     * folder of that {@link File} is created if it does not already exist, and then the {@link File} is used to
     * construct a {@link FileOutputStream}.
     *
     * This method is similar to {@link org.gradle.process.ExecSpec@setStandardOutput} but the provided value may be a
     * template which is interpreted separately for each task.  There is no matching {@code getStandardOutput} method
     * because there may be no single {@link OutputStream} to return: there may be one such stream for each task.
     *
     * @param teeTarget An {@link OutputStream} or another kind of object interpreted as for
     * {@link Project#file(java.lang.Object)}
     */
    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void setStandardOutput(Object standardOutput) {
        this.standardOutput = standardOutput
    }

    /**
     * Redirects the standard output of the test executable to both Gradle's standard output and the given
     * {@code target}.  If the {@code target} is an {@link OutputStream} it is used directly.  Otherwise it is
     * interpreted as for {@link Project#file(java.lang.Object)}, any "&lt;flavour&gt;" strings are replaced in the
     * filename, the parent folder of that {@link File} is created if it does not already exist, and then the
     * {@link File} is used to construct a {@link FileOutputStream}.
     *
     * @param teeTarget An {@link OutputStream} or another kind of object interpreted as for
     * {@link Project#file(java.lang.Object)}
     */
    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void setStandardOutputTee(Object target) {
        this.teeTarget = target
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

    private OutputStream makeOutputStream(String flavour, Object target) {
        if (target instanceof OutputStream) {
            return (OutputStream)target
        } else {
            File outputFile = project.file(target)
            File flavouredOutputFile = new File(replaceFlavour(outputFile.toString(), flavour))
            FileHelper.ensureMkdirs(flavouredOutputFile.parentFile, "as parent folder for test output")
            return new FileOutputStream(flavouredOutputFile)
        }
    }

    private void configureTask(String flavour, Exec task) {
        if (selectedFlavours.contains(flavour)) {
            task.onlyIf {
                boolean commanLineEmpty = (commandLineChunks.size() == 0)
                if (commanLineEmpty) {
                    println "Not running unit test ${name} (${flavour}) because command line is empty."
                }
                return !commanLineEmpty
            }

            // ---- Set up the command line.
            List<String> cmd = commandLineChunks.collect { replaceFlavour(it, flavour) }
            // Look for the command exe one of three places,
            // But proceed even if we don't find it as it may
            // be relying on system path (e.g., "cmd.exe")
            File exePath = new File(cmd[0])
            if (!exePath.exists()) {
                // TODO 2015-03-21 HughG: Remove this at next major revision change.
                File tryPath = new File(project.projectDir, cmd[0])
                if (tryPath.exists()) {
                    project.logger.warn(
                        "Test executable was specified as '${exePath}' but found relative to project.projectDir at " +
                        "'${tryPath}'.  This automatic path search will be removed in a future version of the Holy " +
                        "Gradle.  Please add project.projectDir to the executable path explicitly."
                    )
                    exePath = tryPath
                }
            }
            if (!exePath.exists()) {
                // TODO 2015-03-21 HughG: Remove this at next major revision change.
                File tryPath = new File(project.rootProject.projectDir, cmd[0])
                if (tryPath.exists()) {
                    project.logger.warn(
                        "Test executable was specified as '${exePath}' but found relative to " +
                        "project.rootProject.projectDir at '${tryPath}'.  This automatic path search will be removed " +
                        "in a future version of the Holy Gradle.  Please add project.rootProject.projectDir to the " +
                        "executable path explicitly."
                    )
                    exePath = tryPath
                }
            }
            cmd[0] = exePath.path
            task.commandLine cmd

            // ---- Set up the output stream.
            final OutputStream testOutputStream
            if (standardOutput != null) {
                testOutputStream = makeOutputStream(flavour, standardOutput)
            } else if (redirectOutputFilePath != null) {
                testOutputStream = makeOutputStream(flavour, new File(project.projectDir, redirectOutputFilePath))
            } else {
                testOutputStream = task.standardOutput
            }
            if (task.standardOutput != testOutputStream) {
                task.standardOutput = testOutputStream
            }
            if (teeTarget != null) {
                task.standardOutput = new TeeOutputStream(task.standardOutput, makeOutputStream(flavour, teeTarget))
            }

            // ---- Set up the workingDir.
            if (workingDir != null) {
                task.workingDir workingDir
            }
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


            if (anyBuildDependencies) {
                // Dummy task which just exists to give deprecation information to users.
                // TODO 2015-01-30 HughG: Remove this at next major revision change.
                Task dummyTask = project.task("unitTest${flavour}Independently", type: DefaultTask)
                dummyTask.group = "Unit Test"
                dummyTask.description = "Runs the ${flavour} unit tests for '${project.name}'. " +
                    "Deprecated; use 'gw -a unitTest${flavour}' instead."
                dummyTask.doFirst {
                    throw new RuntimeException(
                        "unitTest${flavour}Independently is deprecated; use 'gw -a unitTest${flavour}' instead"
                    )
                }
            }
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
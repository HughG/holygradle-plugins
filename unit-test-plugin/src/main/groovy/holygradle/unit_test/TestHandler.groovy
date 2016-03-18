package holygradle.unit_test

import holygradle.io.FileHelper
import holygradle.source_dependencies.SourceDependenciesStateHandler
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Exec

class TestHandler {
    public static final Collection<String> DEFAULT_FLAVOURS = Collections.unmodifiableCollection(["Debug", "Release"])

    public final String name
    private Collection<String> commandLineChunks = []
    private Object teeTarget
    private String redirectOutputFilePath
    private Object workingDir
    private Collection<String> selectedFlavours = new ArrayList<String>(DEFAULT_FLAVOURS)
    
    public static TestHandler createContainer(Project project) {
        project.extensions.tests = project.container(TestHandler)
        project.extensions.tests
    }
    
    public TestHandler(String name) {
        this.name = name
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void commandLine(String... cmdLine) {
        commandLineChunks.addAll(cmdLine)
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void redirectOutputToFile(String outputFilePath) {
        redirectOutputFilePath = outputFilePath

        // TODO 2016-03-18 HUGR: Deprecate in favour of standardOutput method.
   }

    @SuppressWarnings("GroovyUnusedDeclaration") // This is an API for build scripts.
    public void teeStandardOutput(Object teeTarget) {
        this.teeTarget = teeTarget
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
                File tryPath = new File(task.project.projectDir, cmd[0])
                if (tryPath.exists()) {
                    exePath = tryPath
                }
            }
            if (!exePath.exists()) {
                File tryPath = new File(task.project.rootProject.projectDir, cmd[0])
                if (tryPath.exists()) {
                    exePath = tryPath
                }
            }
            cmd[0] = exePath.path
            task.commandLine cmd

            // ---- Set up the output stream.
            OutputStream testOutputStream
            File testOutputFile
            if (redirectOutputFilePath != null) {
                testOutputFile = new File(task.project.projectDir, redirectOutputFilePath)
            } else if (teeTarget != null) {
                if (teeTarget instanceof OutputStream) {
                    testOutputStream = (OutputStream)teeTarget
                } else {
                    testOutputFile = task.project.file(teeTarget)
                }
            }
            if (testOutputFile != null) {
                testOutputFile = new File(replaceFlavour(testOutputFile.toString(), flavour))
                FileHelper.ensureMkdirs(testOutputFile.parentFile, "as parent folder for test output")
                testOutputStream = new FileOutputStream(testOutputFile)
            }
            if (testOutputStream != null) {
                if (teeTarget != null) {
                    testOutputStream = new TeeOutputStream(task.standardOutput, testOutputStream)
                }
                task.standardOutput = testOutputStream
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
package holygradle.unit_test

import holygradle.source_dependencies.SourceDependenciesStateHandler
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.process.ExecSpec


class TestHandler {
    public static final Collection<String> DEFAULT_FLAVOURS = Collections.unmodifiableCollection(["Debug", "Release"])

    public final String name
    private Collection<String> commandLineChunks = []
    private String redirectOutputFilePath = null
    private Collection<String> selectedFlavours = new ArrayList<String>(DEFAULT_FLAVOURS)
    
    public static TestHandler createContainer(Project project) {
        project.extensions.tests = project.container(TestHandler)
        project.extensions.tests
    }
    
    public TestHandler(String name) {
        this.name = name
    }
    
    public void commandLine(String... cmdLine) {
        commandLineChunks.addAll(cmdLine)
    }
    
    public void redirectOutputToFile(String outputFilePath) {
        redirectOutputFilePath = outputFilePath
    }
    
    public void flavour(String... newFlavours) {
        selectedFlavours.clear()
        selectedFlavours.addAll(newFlavours)
    }
    
    private void configureTask(Project project, String flavour, Task task) {
        if (selectedFlavours.contains(flavour)) {
            GString testMessage = "Running unit test ${name} (${flavour})..."
            task.doLast {
                println testMessage
                if (commandLineChunks.size() == 0) {
                    println "    Nothing to run."
                } else {
                    project.exec { ExecSpec spec ->
                        List<String> cmd = commandLineChunks.collect { replaceFlavour(it, flavour) }
                        // Look for the command exe one of three places,
                        // But proceed even if we don't find it as it may
                        // be relying on system path (e.g., "cmd.exe")   
                        File exePath = new File(cmd[0])
                        if (!exePath.exists()) {
                            File tryPath = new File(project.projectDir, cmd[0])
                            if (tryPath.exists()) {
                                exePath = tryPath
                            }
                        }
                        if (!exePath.exists()) {
                            File tryPath = new File(project.rootProject.projectDir, cmd[0])
                            if (tryPath.exists()) {
                                exePath = tryPath
                            }
                        }                        
                        cmd[0] = exePath.path
                        spec.commandLine cmd
                        if (redirectOutputFilePath != null) {
                            spec.standardOutput = new FileOutputStream(
                                "${project.projectDir}/${replaceFlavour(redirectOutputFilePath, flavour)}"
                            )
                        }
                    }
                }
            }
        }
    }
    
    public static void preDefineTasks(Project project) {
        TestFlavourHandler.getAllFlavours(project).each { flavour ->
            project.task("unitTest${flavour}", type: DefaultTask)
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
                dummyTask.description = "Run the ${flavour} unit tests for '${project.name}'. " +
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
                it.configureTask(project, flavour, task)
            }
            
            if (anyBuildDependencies) {
                task.description = "Run the ${flavour} unit tests for '${project.name}' and all dependent projects."
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
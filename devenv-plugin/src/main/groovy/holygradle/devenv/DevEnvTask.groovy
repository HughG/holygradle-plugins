package holygradle.devenv

import holygradle.custom_gradle.plugin_apis.StampingProvider
import holygradle.logging.DefaultStyledTextOutput
import holygradle.logging.ErrorHighlightingOutputStream
import holygradle.logging.StyledTextOutput
import holygradle.source_dependencies.SourceDependenciesStateHandler
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class DevEnvTask extends DefaultTask {
    // What operation (e.g. build/clean) are we running?
    public Operation operation = null
    
    // What configuration (e.g. Debug/Release) are we operating on?
    public String configuration = null
    
    // What platform (e.g. Win32/x64) are we operating on?  Any empty string means "any/all platform(s)".
    public static final String EVERY_PLATFORM = ""
    public String platform = EVERY_PLATFORM
    
    private StyledTextOutput output = null

    public enum Operation {
        BUILD ("build"),
        CLEAN ("clean");

        private final String name
        Operation (String name) { this.name = name }
        @Override public String toString() { return name }
    }

    /**
     * Returns the appropriate name for a {@link DevEnvTask}.
     * @param operation The kind of operation which the task performs.
     * @param platform The platform (x86, Win32), using {@link #EVERY_PLATFORM} for "any/all platform(s)".
     * @param configuration The Visual Studio configuration; must be "Debug" or "Release".
     */
    public static getNameForTask(Operation operation, String platform, String configuration) {
        "${operation}${platform.capitalize()}${configuration}"
    }

    public DevEnvTask() {
        group = "DevEnv"
        output = new DefaultStyledTextOutput(System.out)
    }
    
    public void init(Operation operation, String configuration) {
        this.operation = operation
        this.configuration = configuration
    }
    
    private void addStampingDependencyForProject(Project project) {
        StampingProvider stampingExtension = project.extensions.findByName("stamping") as StampingProvider
        if (stampingExtension != null && stampingExtension.runPriorToBuild) {
            dependsOn project.tasks.findByName(stampingExtension.taskName)
        }
    }

    /**
     * WARNING: Only call this inside a gradle.projectsEvaluated block.
     */
    public void configureBuildTask(DevEnvHandler devEnvHandler, String platform) {
        if (this.operation != Operation.BUILD) {
            throw new RuntimeException("Expected task to have been initialised with 'build' operation.")
        }
        this.platform = platform

        if (devEnvHandler.getVsSolutionFile() != null) {
            addStampingDependencyForProject(project)
            if (project != project.rootProject) {
                addStampingDependencyForProject(project.rootProject)
            }

            doConfigureBuildTask(
                project, devEnvHandler.getBuildToolPath(true), devEnvHandler.getVsSolutionFile(),
                devEnvHandler.useIncredibuild(), platform, configuration, 
                devEnvHandler.getWarningRegexes(), devEnvHandler.getErrorRegexes()
            )
        }
    }

    private void configureTaskDependencies() {
        // Add dependencies to tasks with the same "devenv task configuration" (rather than "Gradle configuration") and
        // operation in dependent projects.
        Collection<SourceDependenciesStateHandler> sourceDependenciesState =
            project.extensions.findByName("sourceDependenciesState") as Collection<SourceDependenciesStateHandler>
        if (sourceDependenciesState == null ||
            !project.gradle.startParameter.buildProjectDependencies
        ) {
            return
        }
        // For each configurations in this project which point into a source dependency, make this project's task
        // depend on the same task in the other project, and on the platform-independent task (if they exist).
        sourceDependenciesState.allConfigurationsPublishingSourceDependencies.each { Configuration config ->
            this.dependsOn config.getTaskDependencyFromProjectDependency(true, this.name)
            String anyPlatformTaskName = getNameForTask(this.operation, EVERY_PLATFORM, this.configuration)
            this.dependsOn config.getTaskDependencyFromProjectDependency(true, anyPlatformTaskName)
        }
    }

    public void configureCleanTask(DevEnvHandler devEnvHandler, String platform) {
        if (this.operation != Operation.CLEAN) {
            throw new RuntimeException("Expected task to have been initialised with 'clean' operation.")
        }
        this.platform = platform
        
        if (devEnvHandler.getVsSolutionFile() != null) {
             doConfigureCleanTask(
                project, devEnvHandler.getBuildToolPath(true), devEnvHandler.getVsSolutionFile(), 
                platform, configuration,
                devEnvHandler.getWarningRegexes(), devEnvHandler.getErrorRegexes()
            ) 
        }
    }
    
    private void doConfigureBuildTask(
        Project project, File buildToolPath, File solutionFile, boolean useIncredibuild, 
        String platform, String configuration, Collection<String> warningRegexes, Collection<String> errorRegexes
    ) {
        String targetSwitch = useIncredibuild ? "/Build" : "/build"
        GString configSwitch = useIncredibuild ? "/cfg=\"${configuration}|${platform}\"" : "${configuration}^|${platform}"
        String buildToolName = useIncredibuild ? "Incredibuild" : "DevEnv"
        File outputFile = new File(solutionFile.getParentFile(), "build_${configuration}_${platform}.txt")
        GString title = "${project.name} (${platform} ${configuration})"
        
        description = "Builds the solution '${solutionFile.name}' with ${buildToolName} in $configuration mode, " +
            "first building all dependent projects. Run 'gw -a ...' to build for this project only."
        configureTask(
            project, title, buildToolPath, solutionFile, targetSwitch, 
            configSwitch, outputFile, warningRegexes, errorRegexes
        ) 
    }
    
    private void doConfigureCleanTask(
        Project project, File buildToolPath, File solutionFile,
        String platform, String configuration, Collection<String> warningRegexes, Collection<String> errorRegexes
    ) {
        String targetSwitch = "/Clean"
        GString configSwitch = "${configuration}^|${platform}"
        File outputFile = new File(solutionFile.getParentFile(), "clean_${configuration}_${platform}.txt")
        GString title = "${project.name} (${platform} ${configuration})"
        
        description = "Cleans the solution '${solutionFile.name}' with DevEnv in $configuration mode, " +
            "first building all dependent projects. Run 'gw -a ...' to build for this project only."
        configureTask(
            project, title, buildToolPath, solutionFile, targetSwitch, 
            configSwitch, outputFile, warningRegexes, errorRegexes
        )
    }
    
    private void configureTask(
        Project project, String title, File buildToolPath, File solutionFile, 
        String targetSwitch, String configSwitch, File outputFile, 
        Collection<String> warningRegexes, Collection<String> errorRegexes
    ) {
        StyledTextOutput styledOutput = this.output
        doLast {
            ErrorHighlightingOutputStream devEnvOutput = new ErrorHighlightingOutputStream(
                title, styledOutput, warningRegexes, errorRegexes
            )

            ExecResult result = project.exec { ExecSpec spec ->
                spec.workingDir project.projectDir.path
                spec.commandLine buildToolPath.path, solutionFile.path, targetSwitch, configSwitch
                spec.setStandardOutput devEnvOutput
                spec.setIgnoreExitValue true
            }

            // Summarise errors and warnings.
            devEnvOutput.summarise()
            
            // Write the entire output to a file.
            outputFile.write(devEnvOutput.toString())

            int exit = result.getExitValue()
            if (exit != 0) {
                throw new RuntimeException("${buildToolPath.name} exited with code $exit.")
            }
        }
        configureTaskDependencies()
    }
}
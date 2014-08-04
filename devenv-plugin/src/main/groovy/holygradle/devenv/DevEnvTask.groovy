package holygradle.devenv

import holygradle.custom_gradle.BuildDependency
import holygradle.custom_gradle.plugin_apis.StampingProvider
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class DevEnvTask extends DefaultTask {
    // Should this task depend on similarly named tasks in dependent projects?
    public boolean independently = false 
    
    // What operation (e.g. build/clean) are we running?
    public String operation = null
    
    // What configuration (e.g. Debug/Release) are we operating on?
    public String configuration = null
    
    // What platform (e.g. Win32/x64) are we operating on?
    public String platform = "any"
    
    private StyledTextOutput output = null
    
    DevEnvTask() {
        group = "DevEnv"
        output = services.get(StyledTextOutputFactory).create(DevEnvTask)
    }
    
    public void init(boolean independently, String operation, String configuration) {
        this.independently = independently
        this.operation = operation
        this.configuration = configuration
    }
    
    private void addStampingDependencyForProject(Project project) {
        StampingProvider stampingExtension = project.extensions.findByName("stamping") as StampingProvider
        if (stampingExtension != null && stampingExtension.runPriorToBuild) {
            dependsOn project.tasks.findByName(stampingExtension.taskName)
        }
    }
    
    public void configureBuildTask(DevEnvHandler devEnvHandler, String platform) {
        if (operation != "build") {
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
        
        configureTaskDependencies()
    }
    
    public void configureTaskDependencies() {
        if (independently) {
            return
        }
        // Add dependencies to tasks with the same configuration and operation in
        // dependent projects.
        Collection<BuildDependency> buildDependencies =
            project.extensions.findByName("buildDependencies") as Collection<BuildDependency>
        if (buildDependencies == null) {
            return
        }
        buildDependencies.each { BuildDependency buildDep ->
            Project depProj = buildDep.getProject(project)
            if (depProj == null) {
                return
            }

            depProj.tasks.each { t ->
                if (t instanceof DevEnvTask &&
                    !t.independently &&
                    t.operation == operation &&
                    t.configuration == configuration &&
                    (t.platform == platform || t.platform == "any")
                ) {
                    this.dependsOn t
                }
            }
        }
    }
    
    public void configureCleanTask(DevEnvHandler devEnvHandler, String platform) {
        if (operation != "clean") {
            throw new RuntimeException("Expected task to have been initialised with 'clean' operation.")
        }
        this.platform = platform
        
        if (devEnvHandler.getVsSolutionFile() != null) {
            configureTaskDependencies()
            doConfigureCleanTask(
                project, devEnvHandler.getBuildToolPath(true), devEnvHandler.getVsSolutionFile(), 
                devEnvHandler.useIncredibuild(), platform, configuration, 
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
        
        description = "Builds the solution '${solutionFile.name}' with ${buildToolName} in $configuration mode."
        configureTask(
            project, title, buildToolPath, solutionFile, targetSwitch, 
            configSwitch, outputFile, warningRegexes, errorRegexes
        ) 
    }
    
    private void doConfigureCleanTask(
        Project project, File buildToolPath, File solutionFile, boolean useIncredibuild, 
        String platform, String configuration, Collection<String> warningRegexes, Collection<String> errorRegexes
    ) {
        String targetSwitch = "/Clean"
        GString configSwitch = "${configuration}^|${platform}"
        File outputFile = new File(solutionFile.getParentFile(), "clean_${configuration}_${platform}.txt")
        GString title = "${project.name} (${platform} ${configuration})"
        
        description = "Cleans the solution '${solutionFile.name}' with DevEnv in $configuration mode."
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
            outputFile.write(devEnvOutput.getFullStreamString())
            
            int exit = result.getExitValue()
            if (exit != 0) {
                throw new RuntimeException("${buildToolPath.name} exited with code $exit.")
            }
        }
    }
}
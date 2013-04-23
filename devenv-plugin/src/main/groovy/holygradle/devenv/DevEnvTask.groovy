package holygradle.devenv

import holygradle.custom_gradle.BuildDependency
import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*
import org.gradle.api.logging.*
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

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
        def stampingExtension = project.extensions.findByName("stamping")
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
            
            // We should rebuild symlinks before running any build task.
            def rebuildSymlinksTask = project.tasks.findByName("rebuildSymlinks")
            if (rebuildSymlinksTask != null) {
                dependsOn rebuildSymlinksTask
            }
            
            configureBuildTask(
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
        def buildDependencies = project.extensions.findByName("buildDependencies")
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
                    dependsOn t
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
            configureCleanTask(
                project, devEnvHandler.getBuildToolPath(true), devEnvHandler.getVsSolutionFile(), 
                devEnvHandler.useIncredibuild(), platform, configuration, 
                devEnvHandler.getWarningRegexes(), devEnvHandler.getErrorRegexes()
            ) 
        }
    }
    
    private void configureBuildTask(
        Project project, File buildToolPath, File solutionFile, boolean useIncredibuild, 
        String platform, String configuration, def warningRegexes, def errorRegexes
    ) {
        def targetSwitch = useIncredibuild ? "/Build" : "/build" 
        def configSwitch = useIncredibuild ? "/cfg=\"${configuration}|${platform}\"" : "${configuration}^|${platform}"
        def buildToolName = useIncredibuild ? "Incredibuild" : "DevEnv"
        def outputFile = new File(solutionFile.getParentFile(), "build_${configuration}_${platform}.txt")
        def title = "${project.name} (${platform} ${configuration})"
        
        description = "Builds the solution '${solutionFile.name}' with ${buildToolName} in $configuration mode."
        configureTask(
            project, title, buildToolPath, solutionFile, targetSwitch, 
            configSwitch, outputFile, warningRegexes, errorRegexes
        ) 
    }
    
    private void configureCleanTask(
        Project project, File buildToolPath, File solutionFile, boolean useIncredibuild, 
        String platform, String configuration, def warningRegexes, def errorRegexes
    ) {
        def targetSwitch = "/Clean" 
        def configSwitch = "${configuration}^|${platform}"
        def outputFile = new File(solutionFile.getParentFile(), "clean_${configuration}_${platform}.txt")
        def title = "${project.name} (${platform} ${configuration})"
        
        description = "Cleans the solution '${solutionFile.name}' with DevEnv in $configuration mode."
        configureTask(
            project, title, buildToolPath, solutionFile, targetSwitch, 
            configSwitch, outputFile, warningRegexes, errorRegexes
        )
    }
    
    private void configureTask(
        Project project, String title, File buildToolPath, File solutionFile, 
        String targetSwitch, String configSwitch, File outputFile, 
        def warningRegexes, def errorRegexes
    ) {
        def styledOutput = this.output
        doLast {
            def devEnvOutput = new ErrorHighlightingOutputStream(title, styledOutput, warningRegexes, errorRegexes)
            def result = project.exec {
                workingDir project.projectDir.path
                commandLine buildToolPath.path, solutionFile.path, targetSwitch, configSwitch
                setStandardOutput devEnvOutput
                setIgnoreExitValue true
            }
            
            // Summarise errors and warnings.
            devEnvOutput.summarise()
            
            // Write the entire output to a file.
            outputFile.write(devEnvOutput.getFullStreamString())
            
            def exit = result.getExitValue()
            if (exit != 0) {
                throw new RuntimeException("${buildToolPath.name} exited with code $exit.")
            }
        }
    }
}
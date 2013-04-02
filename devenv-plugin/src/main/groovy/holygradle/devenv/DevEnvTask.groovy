package holygradle.devenv

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*
import org.gradle.api.logging.*
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

class DevEnvTask extends DefaultTask {
    boolean independently
    String configuration
    StyledTextOutput output
    
    DevEnvTask() {
        group = "DevEnv"
        output = services.get(StyledTextOutputFactory).create(DevEnvTask)
    }
    
    public void init(boolean independently, String configuration) {
        this.independently = independently
        this.configuration = configuration
    }
    
    private void addStampingDependencyForProject(Project project) {
        def stampingExtension = project.extensions.findByName("stamping")
        if (stampingExtension != null && stampingExtension.runPriorToBuild) {
            dependsOn project.tasks.findByName(stampingExtension.taskName)
        }
    }
    
    public void configureBuildTask(DevEnvHandler devEnvHandler, String platform, String configuration) {
        if (devEnvHandler.getVsSolutionFile() != null) {
            addStampingDependencyForProject(project)
            if (project != project.rootProject) {
                addStampingDependencyForProject(project.rootProject)
            }
            
            def rebuildSymlinksTask = project.tasks.findByName("rebuildSymlinks")
            if (rebuildSymlinksTask != null) {
                dependsOn rebuildSymlinksTask
            }
            
            configureBuildTask(project, devEnvHandler.getBuildToolPath(true), devEnvHandler.getVsSolutionFile(), devEnvHandler.useIncredibuild(), platform, configuration, devEnvHandler.getWarningRegexes(), devEnvHandler.getErrorRegexes())       
        }
        
        configureTaskDependencies()
    }
    
    public void configureTaskDependencies() {
        if (!independently) {
            def taskDependencies = project.extensions.findByName("taskDependencies")
            if (taskDependencies != null) {
                def dependentTasks = taskDependencies.get(name)
                logger.info "Adding dependency from ${name} to ${dependentTasks}."
                dependsOn dependentTasks
            }
        }
    }
    
    public void configureCleanTask(DevEnvHandler devEnvHandler, String platform, String configuration) {
        if (devEnvHandler.getVsSolutionFile() != null) {
            ext.lazyConfiguration = {
                def taskDependencies = project.extensions.findByName("taskDependencies")
                if (!independently) dependsOn taskDependencies.get(name)
                configureCleanTask(project, devEnvHandler.getBuildToolPath(true), devEnvHandler.getVsSolutionFile(), devEnvHandler.useIncredibuild(), platform, configuration, devEnvHandler.getWarningRegexes(), devEnvHandler.getErrorRegexes())       
            }
        }
    }
    
    public void configureBuildTask(Project project, File buildToolPath, File solutionFile, boolean useIncredibuild, String platform, String configuration, def warningRegexes, def errorRegexes) {
        def targetSwitch = useIncredibuild ? "/Build" : "/build" 
        def configSwitch = useIncredibuild ? "/cfg=\"${configuration}|${platform}\"" : "${configuration}^|${platform}"
        def buildToolName = useIncredibuild ? "Incredibuild" : "DevEnv"
        def outputFile = new File(solutionFile.getParentFile(), "build_${configuration}_${platform}.txt")
        def title = "${project.name} (${platform} ${configuration})"
        
        description = "Builds the solution '${solutionFile.name}' with ${buildToolName} in $configuration mode."
        configureTask(project, title, buildToolPath, solutionFile, targetSwitch, configSwitch, outputFile, warningRegexes, errorRegexes) 
    }
    
    public void configureCleanTask(Project project, File buildToolPath, File solutionFile, boolean useIncredibuild, String platform, String configuration, def warningRegexes, def errorRegexes) {
        def targetSwitch = "/Clean" 
        def configSwitch = "${configuration}^|${platform}"
        def outputFile = new File(solutionFile.getParentFile(), "clean_${configuration}_${platform}.txt")
        def title = "${project.name} (${platform} ${configuration})"
        
        description = "Cleans the solution '${solutionFile.name}' with DevEnv in $configuration mode."
        configureTask(project, title, buildToolPath, solutionFile, targetSwitch, configSwitch, outputFile, warningRegexes, errorRegexes)
    }
    
    private void configureTask(Project project, String title, File buildToolPath, File solutionFile, String targetSwitch, String configSwitch, File outputFile, def warningRegexes, def errorRegexes) {
        doLast {
            def devEnvOutput = new ErrorHighlightingOutputStream(title, this.output, warningRegexes, errorRegexes)
            def result = project.exec {
                workingDir project.projectDir.path
                commandLine buildToolPath.path, solutionFile.path, targetSwitch, configSwitch
                setStandardOutput devEnvOutput
                setIgnoreExitValue true
            }
            devEnvOutput.summarise()
            
            // Write the entire output to a file.
            outputFile.write(devEnvOutput.getFullStreamString())
            
            //result.rethrowFailure()
            def exit = result.getExitValue()
            if (exit != 0) {
                throw new RuntimeException("${buildToolPath.name} exited with code $exit.")
            }
        }
    }
}
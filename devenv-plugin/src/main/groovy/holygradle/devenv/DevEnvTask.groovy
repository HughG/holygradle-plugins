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
    StyledTextOutput output
    
    DevEnvTask() {
        group = "DevEnv"
        output = services.get(StyledTextOutputFactory).create(DevEnvTask)
    }
    
    public void initBuildTask(Project project, DevEnvHandler devEnvHandler, String configuration) {
        def solutionFile = devEnvHandler.getVsSolutionFile()
        def targetSwitch = devEnvHandler.useIncredibuild() ? "/Build" : "/build" 
        def platform = devEnvHandler.getPlatform()
        def configSwitch = devEnvHandler.useIncredibuild() ? "/cfg=\"${configuration}|${platform}\"" : "${configuration}^|${platform}"
        def buildToolName = devEnvHandler.useIncredibuild() ? "Incredibuild" : "DevEnv"
        def outputFile = new File(solutionFile.getParentFile(), "build_${configuration}_${platform}.txt")
        
        description = "Builds the solution '${solutionFile.name}' with ${buildToolName} in $configuration mode."
        initTask(project, devEnvHandler, true, solutionFile, targetSwitch, configSwitch, outputFile) 
    }
    
    public void initCleanTask(Project project, DevEnvHandler devEnvHandler, String configuration) {
        def solutionFile = devEnvHandler.getVsSolutionFile()
        def targetSwitch = "/Clean" 
        def platform = devEnvHandler.getPlatform()
        def configSwitch = "${configuration}^|${platform}"
        def outputFile = new File(solutionFile.getParentFile(), "clean_${configuration}_${platform}.txt")
        
        description = "Cleans the solution '${solutionFile.name}' with DevEnv in $configuration mode."
        initTask(project, devEnvHandler, false, solutionFile, targetSwitch, configSwitch, outputFile)
    }
    
    private void initTask(Project project, DevEnvHandler devEnvHandler, boolean allowIncredibuild, File solutionFile, String targetSwitch, String configSwitch, File outputFile) {
        doLast {
            def buildToolPath = devEnvHandler.getBuildToolPath(allowIncredibuild)
            def devEnvOutput = new ErrorHighlightingOutputStream(project.name, this.output, devEnvHandler.getWarningRegexes(), devEnvHandler.getErrorRegexes())
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
package holygradle.devenv

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*
import org.gradle.api.logging.*

class DevEnvHandler {
    private Project project
    private DevEnvHandler parentHandler
    private String devEnvVersion = null
    private String vsSolutionFile = null
    private String buildPlatform = null
    private String incredibuildPath = null
    private def warningRegexes = []
    private def errorRegexes = []
    
    public DevEnvHandler(Project project, DevEnvHandler parentHandler) {
        this.project = project
        this.parentHandler = parentHandler
        
        defineErrorRegex(/\d+>Build FAILED/)
        defineErrorRegex(/.* [error|fatal error]+ \w+\d{2,5}:.*/)
        defineWarningRegex(/.* warning \w+\d{2,5}:.*/)
    }
    
    public void version(String ver) {
        devEnvVersion = ver
    }
    
    public void solutionFile(String f) {
        vsSolutionFile = f
    }
    
    public void platform(String p) {
        buildPlatform = p
    }
    
    public void incredibuild(String path) {
        incredibuildPath = path
    }
    
    public void defineWarningRegex(def regex) {
        warningRegexes.add(regex)
    }
    
    public void defineErrorRegex(def regex) {
        errorRegexes.add(regex)
    }
    
    public def getWarningRegexes() {
        warningRegexes
    }
    
    public def getErrorRegexes() {
        errorRegexes
    }
    
    public String getPlatform() {
        if (buildPlatform == null) {
            if (parentHandler != null) {
                parentHandler.getPlatform()
            } else {
                "x64"
            }
        } else {
            buildPlatform
        }
    }
        
    public String getDevEnvVersion() {
        if (devEnvVersion == null) {
            if (parentHandler != null) {
                parentHandler.getDevEnvVersion()
            } else {
                "VS100"
            }
        } else {
            devEnvVersion
        }
    }
    
    public File getDevEnvPath() {
        def chosenDevEnvVersion = getDevEnvVersion()
        def envVarComnTools = System.getenv("${chosenDevEnvVersion}COMNTOOLS")
        if (envVarComnTools == null || envVarComnTools == "") {
            throw new RuntimeException("'version' was set to '${chosenDevEnvVersion}' but the environment variable '${chosenDevEnvVersion}COMNTOOLS' was null or empty.")
        }
        def comnToolsPath = new File(envVarComnTools)
        def devEnvPath = new File(comnToolsPath, "../IDE/devenv.com")
        if (!devEnvPath.exists()) {
            throw new RuntimeException("DevEnv could not be found at '${devEnvPath}'.")
        }
        devEnvPath
    }
    
    public File getBuildToolPath(boolean allowIncredibuild) {
        if (allowIncredibuild && useIncredibuild()) {
            getVerifiedIncredibuildPath()
        } else {
            getDevEnvPath()
        }
    }
    
    public boolean useIncredibuild() {
        getIncredibuildPath() != null
    }
    
    public String getIncredibuildPath() {
        if (incredibuildPath == null) {
            if (parentHandler != null) {
                parentHandler.getIncredibuildPath()
            } else {
                null
            }
        } else {
            incredibuildPath
        }
    }
     
    public File getVerifiedIncredibuildPath() {     
        File f = new File(getIncredibuildPath())
        if (!f.exists()) {
            throw new RuntimeException("The incredibuild path was set to '${f.path}' but nothing exists at that location.")
        }
        f
    }
    
    public File getVsSolutionFile() {
        if (vsSolutionFile == null) {
            null
        } else {
            new File(project.projectDir, vsSolutionFile)
        }
    }
}
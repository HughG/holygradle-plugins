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
    private def buildPlatforms = null
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
    
    public void platform(String... platforms) {
        if (buildPlatforms == null) {
            buildPlatforms = []
        }
        for (p in platforms) {
            buildPlatforms.add(p)
        }
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
    
    public def getPlatforms() {
        if (buildPlatforms == null) {
            if (parentHandler != null) {
                parentHandler.getPlatforms()
            } else {
                ["x64"]
            }
        } else {
            buildPlatforms
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
    
    // Returns two tasks - one for building this project as well as dependent projects, and
    // another task for building this project independently.
    public def defineBuildTasks(Project project, String taskName, String configuration) {
        [defineBuildTask(project, taskName, configuration, true),
        defineBuildTask(project, taskName, configuration, false)]
    }
    
    public Task defineBuildTask(Project project, String taskName, String configuration, boolean independently) {
        if (independently) taskName = "${taskName}Independently"
        project.task(taskName, type: DevEnvTask) {
            init(independently, configuration)
            if (independently) {
                description = "This task only makes sense for individual projects e.g. gw subproj:b${configuration[0]}I"
            } else {
                description = "Builds all dependent projects in $configuration mode."
            }
        }
    }
    
    // Returns two tasks - one for cleaning this project as well as dependent projects, and
    // another task for cleaning this project independently.
    public def defineCleanTasks(Project project, String taskName, String configuration) {
        [defineCleanTask(project, taskName, configuration, true),
        defineCleanTask(project, taskName, configuration, false)]
    }
    
    public Task defineCleanTask(Project project, String taskName, String configuration, boolean independently) {
        if (independently) taskName = "${taskName}Independently"
        project.task(taskName, type: DevEnvTask) {
            init(independently, configuration)
            if (independently) {
                description = "This task only makes sense for individual projects e.g. gw subproj:c${configuration[0]}I"
            } else {
                description = "Cleans all dependent projects in $configuration mode."
            }
        }
    }
}
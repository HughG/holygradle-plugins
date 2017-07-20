package holygradle.devenv

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.process.ExecSpec
import holygradle.process.ExecHelper

import java.util.regex.Pattern
import java.util.regex.Matcher

class DevEnvHandler {
    private Project project
    private DevEnvHandler parentHandler
    private String devEnvVersion = null
    private String vsSolutionFile = null
    private List<String> buildPlatforms = null
    private String incredibuildPath = null
    private List<String> warningRegexes = []
    private List<String> errorRegexes = []
    private Boolean shouldCheckForVSWhere = true
    private String vswhereLocation = null
    
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
        buildPlatforms.addAll(platforms)
    }
    
    public void incredibuild(String path) {
        incredibuildPath = path
    }
    
    public void defineWarningRegex(String regex) {
        warningRegexes.add(regex)
    }
    
    public void defineErrorRegex(String regex) {
        errorRegexes.add(regex)
    }
    
    public List<String> getWarningRegexes() {
        warningRegexes
    }
    
    public List<String> getErrorRegexes() {
        errorRegexes
    }
    
    public List<String> getPlatforms() {
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
                throw new RuntimeException(
                    "You must set the devenv version, for example, 'DevEnv { version \"VS120\"}' for the " +
                    "Visual Studio 2013 compiler, or \"VS100\" for the Visual Studio 2010 compiler. " +
                    "This value is used to read the appropriate environment variable, for example, VS120COMMONTOOLS."
                )
            }
        } else {
            devEnvVersion
        }
    }
    
    public File getDevEnvPathFromvswhere() {
        String chosenDevEnvVersion = getDevEnvVersion()
        
        String devEnvLocationPropertyName = "holygradleDevEnv" +chosenDevEnvVersion+"Location";
        if (project.rootProject.hasProperty(devEnvLocationPropertyName)) {
            return project.rootProject.ext[devEnvLocationPropertyName];
        }
        
        Pattern versionPattern = Pattern.compile("VS([0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher versionMatcher = versionPattern.matcher(chosenDevEnvVersion);
        
        if (versionMatcher.find()){
            Integer devEnvVersion = Integer.parseInt(versionMatcher.group(1))
            String decimalVersionNumber = (devEnvVersion/10).toString()
            String nextDecimalVersionNumber = ((devEnvVersion/10)+0.1).toString()
            
            String installPath = ExecHelper.executeAndReturnResultAsString(
                project.logger,
                project.&exec,
                { ExecSpec spec ->
                    spec.commandLine this.vswhereLocation, "-property","installationPath","-legacy","-format","value","-version","[${decimalVersionNumber},${nextDecimalVersionNumber})"
                },
                { return true }
            )
            File devEnvPath = new File(installPath, "/Common7/IDE/devenv.com");
            
            if (!devEnvPath.exists()) {
                throw new RuntimeException("DevEnv could not be found at '${devEnvPath}'.")
            }
            
            project.rootProject.ext[devEnvLocationPropertyName] = devEnvPath
            devEnvPath
            
        } else {
            throw new RuntimeException("Failed to parse DevEnv version '${chosenDevEnvVersion}'")
            
        }
        
    }
    
    public File getDevEnvPathFromRegistry() {
        String chosenDevEnvVersion = getDevEnvVersion()
        String envVarComnTools = System.getenv("${chosenDevEnvVersion}COMNTOOLS")
        if (envVarComnTools == null || envVarComnTools == "") {
            throw new RuntimeException("'version' was set to '${chosenDevEnvVersion}' but the environment variable '${chosenDevEnvVersion}COMNTOOLS' was null or empty.")
        }
        File comnToolsPath = new File(envVarComnTools)
        File devEnvPath = new File(comnToolsPath, "../IDE/devenv.com")
        if (!devEnvPath.exists()) {
            throw new RuntimeException("DevEnv could not be found at '${devEnvPath}'.")
        }
        devEnvPath
    }
     
    public File getDevEnvPath() {
        
        // Check for the existence of vswhere.exe
        if (this.shouldCheckForVSWhere) {
            
            this.shouldCheckForVSWhere = false;
            String programFiles = System.getenv("ProgramFiles(x86)");
            String programFiles32 = System.getenv("ProgramFiles"); //  Location of vswhere.exe on 32 bit OS's before windows 10.
            if (programFiles != null &&
                new File("${programFiles}\\Microsoft Visual Studio\\Installer\\vswhere.exe").exists()) {
                    this.vswhereLocation = "${programFiles}\\Microsoft Visual Studio\\Installer\\vswhere.exe";   
            } else if (programFiles32 != null && 
                new File("${programFiles32}\\Microsoft Visual Studio\\Installer\\vswhere.exe").exists()) {
                    this.vswhereLocation = "${programFiles32}\\Microsoft Visual Studio\\Installer\\vswhere.exe";
            }
        }
                
        if (this.vswhereLocation != null){
            getDevEnvPathFromvswhere();
        } else {
            getDevEnvPathFromRegistry();
        }
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
    public List<DevEnvTask> defineBuildTasks(Project project, String platform, String configuration) {
        [defineBuildTask(project, platform, configuration, true),
        defineBuildTask(project, platform, configuration, false)]
    }
    
    public DevEnvTask defineBuildTask(Project project, String platform, String configuration, boolean independently) {
        String taskName = DevEnvTask.getNameForTask(DevEnvTask.Operation.BUILD, platform, configuration, independently)
        (DevEnvTask) project.task(taskName, type: DevEnvTask) { DevEnvTask it ->
            it.init(independently, DevEnvTask.Operation.BUILD, configuration)
            if (independently) {
                def normalTaskName = DevEnvTask.getNameForTask(DevEnvTask.Operation.BUILD, platform, configuration, false)
                description = "This task only makes sense for individual projects e.g. gw subproj:b${configuration[0]}I. " +
                    "Deprecated; use 'gw -a ${normalTaskName}' instead."
            } else {
                description = "Builds all dependent projects in $configuration mode."
            }
        }
    }
    
    // Returns two tasks - one for cleaning this project as well as dependent projects, and
    // another task for cleaning this project independently.
    public List<DevEnvTask> defineCleanTasks(Project project, String platform, String configuration) {
        [defineCleanTask(project, platform, configuration, true),
        defineCleanTask(project, platform, configuration, false)]
    }
    
    public DevEnvTask defineCleanTask(Project project, String platform, String configuration, boolean independently) {
        String taskName = DevEnvTask.getNameForTask(DevEnvTask.Operation.CLEAN, platform, configuration, independently)
        (DevEnvTask) project.task(taskName, type: DevEnvTask) { DevEnvTask it ->
            it.init(independently, DevEnvTask.Operation.CLEAN, configuration)
            if (independently) {
                def normalTaskName = DevEnvTask.getNameForTask(DevEnvTask.Operation.CLEAN, platform, configuration, false)
                description = "This task only makes sense for individual projects e.g. gw subproj:c${configuration[0]}I. " +
                    "Deprecated; use 'gw -a ${normalTaskName}' instead."
            } else {
                description = "Cleans all dependent projects in $configuration mode."
            }
        }
    }
}
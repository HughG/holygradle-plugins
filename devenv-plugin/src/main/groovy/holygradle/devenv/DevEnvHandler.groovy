package holygradle.devenv

import org.gradle.api.Project
import org.gradle.process.ExecSpec
import holygradle.process.ExecHelper

import java.util.regex.Pattern
import java.util.regex.Matcher

class DevEnvHandler {
    private static final Pattern VERSION_PATTERN =
        Pattern.compile(
            /(VS([0-9][0-9]?)0)|/ + // "VSnnnCOMNTOOLS" environment variable
            /([0-9]+)\.([0-9]+|\+))/, // "nn.n" vswhere version string, also allowing "nn.+"
            Pattern.CASE_INSENSITIVE
        )

    private Project project
    private DevEnvHandler parentHandler
    private String devEnvVersion = null
    private String vsSolutionFile = null
    private List<String> buildPlatforms = null
    private String incredibuildPath = null
    private List<String> warningRegexes = []
    private List<String> errorRegexes = []
    public String vswhereLocation = null // Only public so it can be accessed from inside a closure.  Stupid Groovy.

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
                    "From Visual Studio 2017 onwards you can use a string without the \"VS\" prefix, " +
                    "such as \"15.2\" to get an exact version match, or \"15.+\" to match only the major version. " +
                    "This value is used to find the appropriate path to 'devenv.com'."
                )
            }
        } else {
            devEnvVersion
        }
    }

    public File getCommonToolsPathFromVSWhere() {
        final String chosenDevEnvVersion = getDevEnvVersion()
        final Matcher versionMatcher = VERSION_PATTERN.matcher(chosenDevEnvVersion)
        
        if (versionMatcher.find()) {
            final Integer majorVersionNumber = Integer.parseInt(versionMatcher.group(1))
            final String minorVersion = versionMatcher.group(2)

            final String versionRangeStart
            final String versionRangeEnd
            if (chosenDevEnvVersion.startsWith("VS") || minorVersion == "+") {
                versionRangeStart = "${majorVersionNumber}.0"
                versionRangeEnd = "${majorVersionNumber + 1}.0"
            } else {
                versionRangeStart = "${majorVersionNumber}.${minorVersion}"
                final Integer minorVersionNumber = Integer.parseInt(minorVersion)
                versionRangeEnd = "${majorVersionNumber + 1}.${minorVersionNumber + 1}"
            }

            String installPath = ExecHelper.executeAndReturnResultAsString(
                project.logger,
                project.&exec,
                { ExecSpec spec ->
                    spec.commandLine this.vswhereLocation,
                                     "-property", "installationPath",
                                     "-legacy",
                                     "-format", "value",
                                     "-version", "[${versionRangeStart},${versionRangeEnd})"
                },
                { return true }
            )
            return new File(installPath, "Common7/Tools")
        } else {
            throw new RuntimeException("Failed to parse DevEnv version '${chosenDevEnvVersion}'")
        }
    }

    public String getCommonToolsEnvironmentVariableName() {
        final String chosenDevEnvVersion = getDevEnvVersion()
        if (chosenDevEnvVersion.startsWith("VS")) {
            return "${chosenDevEnvVersion}COMNTOOLS"
        } else {
            return null
        }
    }

    public File getCommonToolsPathFromEnvironment() {
        String envVarName = getCommonToolsEnvironmentVariableName()
        if (envVarName == null) {
            return null
        } else {
            String envVarComnTools = System.getenv(envVarName)
            if (envVarComnTools == null || envVarComnTools == "") {
                return null
            } else {
                return new File(envVarComnTools)
            }
        }
    }

    public File getCommonToolsPath() {
        final File pathFromEnvironment = getCommonToolsPathFromEnvironment()
        if (pathFromEnvironment == null || !pathFromEnvironment.exists()) {
            // Check for the existence of vswhere.exe
            final List<String> vswherePossibleLocations =
                ["ProgramFiles", "ProgramFiles(x86)"] // look at 64-bit then 32-bit environment variables
                .findResults { String it -> System.getenv(it) } // collect all non-null environment variable values
                .collect { "${it}\\Microsoft Visual Studio\\Installer\\vswhere.exe".toString() } // map to filenames
            this.vswhereLocation = vswherePossibleLocations
                .find { new File(it).exists() } // return the first filename for which a file exists

            if (this.vswhereLocation != null) {
                return getCommonToolsPathFromVSWhere()
            } else {
                // Failed; now we need to pick the right error message.
                def envVarComnTools = getCommonToolsEnvironmentVariableName()
                if (envVarComnTools == null) {
                    throw new RuntimeException(
                        "Cannot find \"vswhere.exe\" to locate \"devenv.com\" " +
                        "(should be in ${vswherePossibleLocations})."
                    )
                } else if (!pathFromEnvironment.exists()) {
                    throw new RuntimeException(
                        "Environment variable ${envVarComnTools} is set, but " +
                        "its target (${pathFromEnvironment}) does not exist, and " +
                        "cannot find \"vswhere.exe\" to locate \"devenv.com\" " +
                        "(should be in ${vswherePossibleLocations})."
                    )
                } else {
                    throw new RuntimeException(
                        "Environment variable ${envVarComnTools} is not set, and " +
                        "cannot find \"vswhere.exe\" to locate \"devenv.com\" " +
                        "(should be in ${vswherePossibleLocations})."
                    )
                }
            }
        } else {
            return pathFromEnvironment
        }
    }

    public File getDevEnvPath() {
        String chosenDevEnvVersion = getDevEnvVersion()
        final String devEnvLocationPropertyName = "holygradleDevEnv${chosenDevEnvVersion}Location"
        if (project.rootProject.hasProperty(devEnvLocationPropertyName)) {
            return project.rootProject.ext[devEnvLocationPropertyName] as File
        }

        File commonToolsPath = getCommonToolsPath()
        File devEnvPath = new File(commonToolsPath, "../IDE/devenv.com")
        if (!devEnvPath.exists()) {
            throw new RuntimeException("DevEnv could not be found at '${devEnvPath}'.")
        }

        project.rootProject.ext[devEnvLocationPropertyName] = devEnvPath
        return devEnvPath
    }
    
    public File getBuildToolPath() {
        if (useIncredibuild()) {
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
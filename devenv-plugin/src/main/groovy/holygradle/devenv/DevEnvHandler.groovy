package holygradle.devenv

import org.gradle.api.Project
import org.gradle.process.ExecSpec
import holygradle.process.ExecHelper

import java.util.regex.Pattern
import java.util.regex.Matcher

class DevEnvHandler {
    private Project project
    private DevEnvHandler parentHandler
    private VSVersion devEnvVersion = null
    private String vsSolutionFile = null
    private List<String> buildPlatforms = null
    private String incredibuildPath = null
    private List<String> warningRegexes = []
    private List<String> errorRegexes = []
    public String vswhereLocation = null // Only public so it can be accessed from inside a closure.  Stupid Groovy.

    public static class VSVersion {
        private static final Pattern VERSION_PATTERN =
            Pattern.compile(
                /VS([0-9]+)(0)|/ + // "VSnnnCOMNTOOLS" environment variable
                /([0-9]+)\.([0-9]+|\+)/, // "nn.n" vswhere version string, also allowing "nn.+"
                Pattern.CASE_INSENSITIVE
            )

        private final String asString

        public final String major
        public final String minor
        public final String rangeStart
        public final String rangeEnd
        public final String envVarName

        public VSVersion(final String version) {
            asString = version
            Matcher versionMatcher = match(version)
            if (versionMatcher.group(1) != null) {
                // Matched "VSnnn"
                major = versionMatcher.group(1)
                minor = versionMatcher.group(2)
            } else {
                // Matched "nn.x"
                major = versionMatcher.group(3)
                minor = versionMatcher.group(4)
            }

            final char firstChar = version.charAt(0)
            final boolean isEnvVarStyle = (Character.toLowerCase(firstChar) == ('v' as char))
            final boolean isWildcardMinorVersion = isEnvVarStyle || (this.minor == '+')
            envVarName = "VS${major}0COMNTOOLS"
            if (isWildcardMinorVersion) {
                rangeStart = "${major}.0"
                rangeEnd = "${major.toInteger() + 1}.0"
            } else {
                rangeStart = "${major}.${this.minor}"
                rangeEnd = "${major}.${minor.toInteger() + 1}"
            }
        }

        // Pulled out as a static method because IntelliJ gives "field may be uninitialised" warnings otherwise.
        private static Matcher match(String version) {
            final Matcher versionMatcher = VERSION_PATTERN.matcher(version)
            if (!versionMatcher.find()) {
                throw new RuntimeException("Failed to parse DevEnv version '${version}'")
            }
            return versionMatcher
        }

        @Override
        public String toString() {
            return asString
        }
    }

    public DevEnvHandler(Project project, DevEnvHandler parentHandler) {
        this.project = project
        this.parentHandler = parentHandler
        
        defineErrorRegex(/\d+>Build FAILED/)
        defineErrorRegex(/.* [error|fatal error]+ \w+\d{2,5}:.*/)
        defineWarningRegex(/.* warning \w+\d{2,5}:.*/)
    }
    
    public void version(String ver) {
        devEnvVersion = new VSVersion(ver)
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
        
    public VSVersion getDevEnvVersion() {
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
        final VSVersion version = getDevEnvVersion()
        final String versionRange = "[${version.rangeStart},${version.rangeEnd})"
        final String installPath = ExecHelper.executeAndReturnResultAsString(
            project.logger,
            project.&exec,
            { ExecSpec spec ->
                spec.commandLine this.vswhereLocation,
                                 "-property", "installationPath",
                                 "-legacy",
                                 "-format", "value",
                                 "-version", versionRange
            },
            { return true }
        )
        if (installPath == null || installPath.trim().empty) {
            throw new RuntimeException("vswhere.exe could not find a Visual Studio version in range ${versionRange}")
        }
        return new File(installPath, "Common7/Tools")
    }


    public File getCommonToolsPathFromEnvironment() {
        final String envVarName = getDevEnvVersion().envVarName
        if (envVarName == null) {
            return null
        } else {
            final String envVarComnTools = System.getenv(envVarName)
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
                final String envVarComnTools = getDevEnvVersion().envVarName
                if (envVarComnTools == null) {
                    throw new RuntimeException(
                        "Cannot find \"vswhere.exe\" to locate \"devenv.com\" " +
                        "(should be in ${vswherePossibleLocations})."
                    )
                } else if (pathFromEnvironment == null) {
                    throw new RuntimeException(
                        "Environment variable ${envVarComnTools} is not set, and " +
                        "cannot find \"vswhere.exe\" to locate \"devenv.com\" " +
                        "(should be in ${vswherePossibleLocations})."
                    )
                } else /* !pathFromEnvironment.exists() */ {
                    throw new RuntimeException(
                        "Environment variable ${envVarComnTools} is set, but " +
                        "its target (${pathFromEnvironment}) does not exist, and " +
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
        VSVersion version = getDevEnvVersion()

        // Check if the version was already cached in the root project.
        final String devEnvLocationPropertyName =
            "holygradleDevEnv_${version.major}_${version.minor}_Location"
        if (project != project.rootProject && project.rootProject.hasProperty(devEnvLocationPropertyName)) {
            return project.rootProject.ext[devEnvLocationPropertyName] as File
        }

        File commonToolsPath = getCommonToolsPath()
        File devEnvPath = new File(commonToolsPath, "../IDE/devenv.com")
        if (!devEnvPath.exists()) {
            throw new RuntimeException("DevEnv could not be found at '${devEnvPath}'.")
        }

        // Cache location in the root project, so we don't have to run vswhere for every sub-project.
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
    public DevEnvTask defineBuildTask(Project project, String platform, String configuration) {
        String taskName = DevEnvTask.getNameForTask(DevEnvTask.Operation.BUILD, platform, configuration)
        (DevEnvTask) project.task(taskName, type: DevEnvTask) { DevEnvTask it ->
            it.init(DevEnvTask.Operation.BUILD, configuration)
            it.description = "Builds all dependent projects in $configuration mode."
        }
    }
    
    // Returns two tasks - one for cleaning this project as well as dependent projects, and
    // another task for cleaning this project independently.
    public DevEnvTask defineCleanTask(Project project, String platform, String configuration) {
        String taskName = DevEnvTask.getNameForTask(DevEnvTask.Operation.CLEAN, platform, configuration)
        (DevEnvTask) project.task(taskName, type: DevEnvTask) { DevEnvTask it ->
            it.init(DevEnvTask.Operation.CLEAN, configuration)
            it.description = "Cleans all dependent projects in $configuration mode."
        }
    }
}
package holygradle.devenv

import holygradle.kotlin.dsl.extra
import org.gradle.api.Project
import holygradle.kotlin.dsl.task
import holygradle.process.ExecHelper
import org.gradle.api.Action
import org.gradle.process.ExecSpec
import java.io.File
import java.util.function.Predicate
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Copyright (c) 2016 Hugh Greene (githugh@tameter.org).
 */

private class VSVersion(private val version: String) {
    private val versionPattern = Pattern.compile(
            "VS([0-9]+)(0)|" + // "VSnnnCOMNTOOLS" environment variable
                    "([0-9]+)\\.([0-9]+|\\+)", // "nn.n" vswhere version string, also allowing "nn.+"
            Pattern.CASE_INSENSITIVE
    )

    val major: String
    val minor: String
    val rangeStart: String
    val rangeEnd: String
    val envVarName: String

    init {
        val versionMatcher = match(version)
        if (versionMatcher.group(1) != null) {
            // Matched "VSnnn"
            major = versionMatcher.group(1)
            minor = versionMatcher.group(2)
        } else {
            // Matched "nn.x"
            major = versionMatcher.group(3)
            minor = versionMatcher.group(4)
        }

        val firstChar = version[0]
        val isEnvVarStyle = (Character.toLowerCase(firstChar) == 'v')
        val isWildcardMinorVersion = isEnvVarStyle || (minor == "+")
        envVarName = "VS${major}0COMNTOOLS"
        if (isWildcardMinorVersion) {
            rangeStart = "${major}.0"
            rangeEnd = "${major.toInt() + 1}.0"
        } else {
            rangeStart = "${major}.${minor}"
            rangeEnd = "${major}.${minor.toInt() + 1}"
        }
    }

    // Pulled out as a static method because IntelliJ gives "field may be uninitialised" warnings otherwise.
    private fun match(version: String): Matcher {
        val versionMatcher = versionPattern.matcher(version)
        if (!versionMatcher.find()) {
            throw RuntimeException("Failed to parse DevEnv version '${version}'")
        }
        return versionMatcher
    }

    override fun toString(): String = version
}

/**
 *  NOTE: If you change the Visual Studio location code in this class, also change it in credential-store/build.gradle. 
 */
open class DevEnvHandler(
        private val project: Project,
        private val parentHandler: DevEnvHandler?
) {
    internal val errorRegexes: MutableList<Pattern> = mutableListOf()
    internal val warningRegexes: MutableList<Pattern> = mutableListOf()

    // The init block needs to come after the regex fields are initialised, or else the defineXxxRegex methods throw NPE.
    init {
        defineErrorRegex("\\d+>Build FAILED".toRegex())
        defineErrorRegex(".* (error|fatal error) \\w+\\d{2,5}:.*".toRegex())
        defineWarningRegex(".* warning \\w+\\d{2,5}:.*".toRegex())
    }

    private var _devEnvVersion: VSVersion? = null
    private val devEnvVersion: VSVersion by lazy {
        _devEnvVersion ?: parentHandler?._devEnvVersion ?:
                throw RuntimeException(
                        "You must set the devenv version, for example, 'DevEnv { version \"VS120\"}' for the " +
                        "Visual Studio 2013 compiler, or \"VS100\" for the Visual Studio 2010 compiler. " +
                        "This value is used to read the appropriate environment variable, for example, VS120COMMONTOOLS."
                )
    }

    private var _vsSolutionFile: String? = null
    val vsSolutionFile: File? get() {
        return if (_vsSolutionFile == null) {
            null
        } else {
            File(project.projectDir, _vsSolutionFile)
        }
    }

    private var _platforms: MutableList<String> = mutableListOf()
    val platforms: List<String> get() {
        return if (_platforms.isNotEmpty()) {
            _platforms
        } else {
            val parentPlatforms = parentHandler?._platforms
            if (parentPlatforms != null && parentPlatforms.isNotEmpty()) {
                parentPlatforms
            } else {
                listOf("x64")
            }
        }
    }

    private var _incredibuildPath: String? = null
    private val incredibuildPath: String? get() =
        _incredibuildPath ?: parentHandler?._incredibuildPath
    private val verifiedIncredibuildPath: File
        get() {
            if (incredibuildPath == null) {
                throw RuntimeException("The incredibuild path was not set")
            }
            val f = File(incredibuildPath)
            if (!f.exists()) {
                throw RuntimeException(
                        "The incredibuild path was set to '${f.path}' but nothing exists at that location."
                )
            }
            return f
        }

    private var vswhereLocation: String? = null

    fun version(version: String) {
        _devEnvVersion = VSVersion(version)
    }

    fun solutionFile(f: String) {
        _vsSolutionFile = f
    }

    fun platform(vararg platform: String) {
        _platforms.addAll(platform)
    }

    fun incredibuild(path: String) {
        _incredibuildPath = path
    }

    fun defineErrorRegex(regex: Regex) {
        errorRegexes.add(regex.toPattern())
    }

    fun defineWarningRegex(regex: Regex) {
        warningRegexes.add(regex.toPattern())
    }

    fun defineErrorRegex(regex: String) {
        errorRegexes.add(Pattern.compile(regex))
    }

    fun defineWarningRegex(regex: String) {
        warningRegexes.add(Pattern.compile(regex))
    }

    private fun getCommonToolsPathFromVSWhere(): File {
        val version = devEnvVersion
        val versionRange = "[${version.rangeStart},${version.rangeEnd})"
        val installPath = ExecHelper.executeAndReturnResultAsString(
                project.logger,
                project::exec,
                Action { spec: ExecSpec ->
                    spec.commandLine(this.vswhereLocation,
                            "-property", "installationPath",
                            "-legacy",
                            "-format", "value",
                            "-version", versionRange)
                },
                Predicate { true }
        )
        if (installPath.trim().isEmpty()) {
            throw RuntimeException("vswhere.exe could not find a Visual Studio version in range ${versionRange}")
        }
        return File(installPath, "Common7/Tools")
    }


    private fun getCommonToolsPathFromEnvironment(): File? {
        val envVarComnTools = System.getenv(devEnvVersion.envVarName)
        return if (envVarComnTools.isNullOrEmpty()) {
            null
        } else {
            File(envVarComnTools)
        }
    }

    private fun getCommonToolsPath(): File {
        val pathFromEnvironment = getCommonToolsPathFromEnvironment()
        if (pathFromEnvironment == null || !pathFromEnvironment.exists()) {
            // Check for the existence of vswhere.exe
            val vswherePossibleLocations =
                    listOf("ProgramFiles", "ProgramFiles(x86)") // look at 64-bit then 32-bit environment variables
                            .mapNotNull { System.getenv(it) } // collect all non-null environment variable values
                            .map { "${it}\\Microsoft Visual Studio\\Installer\\vswhere.exe" } // map to filenames
            this.vswhereLocation = vswherePossibleLocations
                    .find { File(it).exists() } // return the first filename for which a file exists

            if (this.vswhereLocation != null) {
                return getCommonToolsPathFromVSWhere()
            } else {
                // Failed; now we need to pick the right error message.
                val envVarComnTools = devEnvVersion.envVarName
                throw RuntimeException(when {
                    this.vswhereLocation == null ->
                            "Cannot find \"vswhere.exe\" to locate \"devenv.com\" " +
                                    "(should be in ${vswherePossibleLocations})."
                    pathFromEnvironment == null ->
                            "Environment variable ${envVarComnTools} is not set, and " +
                                    "cannot find \"vswhere.exe\" to locate \"devenv.com\" " +
                                    "(should be in ${vswherePossibleLocations})."
                    else /* !pathFromEnvironment.exists() */ ->
                            "Environment variable ${envVarComnTools} is set, but " +
                                    "its target (${pathFromEnvironment}) does not exist, and " +
                                    "cannot find \"vswhere.exe\" to locate \"devenv.com\" " +
                                    "(should be in ${vswherePossibleLocations})."
                })
            }
        } else {
            return pathFromEnvironment
        }
    }

    private val devEnvPath: File
        by lazy {
            val version = devEnvVersion

            // Check if the version was already cached in the root project.
            val devEnvLocationPropertyName =
                    "holygradleDevEnv_${version.major}_${version.minor}_Location"
            if (project != project.rootProject && project.rootProject.hasProperty(devEnvLocationPropertyName)) {
                return@lazy project.rootProject.extra[devEnvLocationPropertyName] as File
            }

            val commonToolsPath = getCommonToolsPath()
            val devEnvPath = File(commonToolsPath, "../IDE/devenv.com")
            if (!devEnvPath.exists()) {
                throw RuntimeException("DevEnv could not be found at '${devEnvPath}'.")
            }

            // Cache location in the root project, so we don't have to run vswhere for every sub-project.
            project.rootProject.extra[devEnvLocationPropertyName] = devEnvPath
            devEnvPath
        }

    val useIncredibuild: Boolean get() = (incredibuildPath != null)

    fun getBuildToolPath(): File {
        return if (useIncredibuild) {
            verifiedIncredibuildPath
        } else {
            devEnvPath
        }
    }

    fun defineBuildTask(project: Project, platform: String, configuration: String): DevEnvTask {
        val taskName = DevEnvTask.getNameForTask(DevEnvTask.Operation.BUILD, platform, configuration)
        return project.task<DevEnvTask>(taskName) {
            init(DevEnvTask.Operation.BUILD, configuration)
            description = "Builds all dependent projects in $configuration mode."
        }
    }

    fun defineCleanTask(project: Project, platform: String, configuration: String): DevEnvTask {
        val taskName = DevEnvTask.getNameForTask(DevEnvTask.Operation.CLEAN, platform, configuration)
        return project.task<DevEnvTask>(taskName) {
            init(DevEnvTask.Operation.CLEAN, configuration)
            description = "Cleans all dependent projects in $configuration mode."
        }
    }
}

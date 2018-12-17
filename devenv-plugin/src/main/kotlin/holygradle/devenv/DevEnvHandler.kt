package holygradle.devenv

import org.gradle.api.Project
import holygradle.kotlin.dsl.task
import java.io.File
import java.util.regex.Pattern

/**
 * Copyright (c) 2016 Hugh Greene (githugh@tameter.org).
 */
open class DevEnvHandler(
        val project: Project,
        val parentHandler: DevEnvHandler?
) {
    val errorRegexes: MutableList<Pattern> = mutableListOf()
    val warningRegexes: MutableList<Pattern> = mutableListOf()

    // The init block needs to come after the regex fields are initialised, or else the defineXxxRegex methods throw NPE.
    init {
        defineErrorRegex("\\d+>Build FAILED".toRegex())
        defineErrorRegex(".* (error|fatal error) \\w+\\d{2,5}:.*".toRegex())
        defineWarningRegex(".* warning \\w+\\d{2,5}:.*".toRegex())
    }

    private var _devEnvVersion: String? = null
    val devEnvVersion: String by lazy {
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

    fun version(version: String) {
        _devEnvVersion = version
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

    val devEnvPath: File
        get() {
            val chosenDevEnvVersion = devEnvVersion
            val envVarName = "${chosenDevEnvVersion}COMNTOOLS"
            val envVarCommonTools = System.getenv(envVarName)
            if (envVarCommonTools.isNullOrEmpty()) {
                throw RuntimeException(
                        "'version' was set to '$chosenDevEnvVersion' but the environment variable " +
                        "'$envVarName' was null or empty."
                )
            }
            val path = File(envVarCommonTools, "../IDE/devenv.com")
            if (!path.exists()) {
                throw RuntimeException("DevEnv could not be found at '$path'.")
            }
            return path
        }

    val useIncredibuild: Boolean get() = (incredibuildPath != null)

    fun getBuildToolPath(allowIncredibuild: Boolean): File {
        return if (allowIncredibuild && useIncredibuild) {
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

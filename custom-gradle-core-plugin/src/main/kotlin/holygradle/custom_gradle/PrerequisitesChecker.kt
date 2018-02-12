package holygradle.custom_gradle

import org.gradle.api.Action
import org.gradle.api.Project
import java.io.ByteArrayOutputStream
import java.io.File

class PrerequisitesChecker(
        val project: Project,
        val name: String,
        private val checkAction: Action<PrerequisitesChecker>,
        val parameter: Array<out Any>?) {
    private var ok = true

    private fun getCheckingAllPrerequisites(): Boolean {
        return PrerequisitesExtension.getPrerequisites(project)?.checkingAllPrerequisites ?: false
    }

    inline fun <reified T> getParametersAs(): Iterable<T>? = parameter?.filterIsInstance<T>()

    fun run(): Boolean {
        // The 'ok' variable is not really stateful. Its purpose is to determine the return value for this method.
        // It will be set to false if the fail method is called.
        ok = true
        if (parameter == null) {
            println("Checking '${name}'...")
        } else {
            println("Checking '${name}' (${parameter})...")
        }
        checkAction.execute(this)
        return ok
    }
    
    fun readRegistry(location: String, key: String): String? {
        val regOutput = ByteArrayOutputStream()
        val execResult = project.exec {
            it.commandLine("cmd", "/c", "reg", "query", "\"${location}\"", "/v", key)
            it.standardOutput = regOutput
            it.errorOutput = ByteArrayOutputStream()
            it.isIgnoreExitValue = true
        }
        if (execResult.exitValue == 0) {
            val line = regOutput.toString().lines()[2]
            return line.split(" ").last()
        }
        return null
    }

    @Deprecated("Use System.getenv instead", ReplaceWith("System.getenv(variable)"))
    fun readEnvironment(variable: String): String? = System.getenv(variable)

    @Deprecated("Use System.getProperty instead", ReplaceWith("System.getProperty(property)"))
    fun readProperty(property: String): String? = System.getProperty(property)

    fun readFile(path: String): String? {
        val f = File(path)
        return when {
            f.exists() && f.isFile() -> f.readText()
            else -> null
        }
    }
    
    fun readFileVersion(path: String): String? {
        val powershellOutput = ByteArrayOutputStream()
        val execResult = project.exec {
            it.commandLine("powershell", "-Command", "(Get-Item '${path}').VersionInfo.FileVersion")
            it.standardOutput = powershellOutput
            it.errorOutput = ByteArrayOutputStream()
            it.isIgnoreExitValue = true
        }
        return when {
            execResult.exitValue == 0 -> powershellOutput.toString().trim()
            else -> null
        }
    }
    
    fun assertOnPath(shouldBeOnPath: String, failureMessage: String? = null) {
        val execResult = project.exec {
            it.commandLine("cmd", "/c", "where", shouldBeOnPath)
            it.standardOutput = ByteArrayOutputStream()
            it.errorOutput = ByteArrayOutputStream()
            it.isIgnoreExitValue = true
        }
        if (execResult.exitValue != 0) {
            fail("Failed to find '${shouldBeOnPath}' on the path. ", failureMessage)
        }
    }
    
    fun assertEnvironmentVariableExists(envVar: String, failureMessage: String? = null) {
        if (System.getenv(envVar) == null) {
            fail("The environment variable '${envVar}' has not been set. ", failureMessage)
        }
    }
     
    fun assertEnvironmentVariableRefersToDirectory(envVar: String, failureMessage: String? = null) {
        val env = System.getenv(envVar)
        if (env == null) {
            fail("The environment variable '${envVar}' has not been set. ", failureMessage)
        } else {
            val dir = File(env)
            if (dir.exists()) {
                if (!dir.isDirectory()) {
                    fail("The environment variable '${envVar}' is set, contains '{env}' but that is not a directory. ", failureMessage)
                }
            } else {
                fail("The environment variable '${envVar}' is set, contains '{env}' but that path does not exist. ", failureMessage)
            }
        }
    }
    
    fun fail(vararg textItems: String?) {
        val text = StringBuilder()
        textItems.forEach {
            if (it != null) {
                text.append(it)
            }
        }
        if (text.isEmpty()) {
            text.append("Prerequisite check '${name}' failed.")
        }
        wrapMessage(text.toString())
        ok = false
        if (!getCheckingAllPrerequisites()) {
            throw RuntimeException("Prerequisite check '${name}' failed.")
        }
    }
    
    private fun wrapMessage(text: String, columns: Int = 80) {
        println("-".repeat(columns))
        text.lines().forEach{ lnText ->
            var line = StringBuilder()
            lnText.split(" ").forEach { word ->
                var currentWord = word
                if (currentWord.length > columns) {
                    val part = currentWord.substring(0, columns - line.length)
                    line.append(part)
                    currentWord = currentWord.substring(part.length)
                }
                if (line.length + currentWord.length > columns) {
                    println(line.trim())
                    line = StringBuilder()
                }
                while (currentWord.length > columns) {
                    println(currentWord.substring(0, columns))
                    currentWord = currentWord.substring(columns)
                }
                line.append(currentWord)
                line.append(" ")
            }
            println(line.trim())
        }
        println("-".repeat(columns))
    }
}
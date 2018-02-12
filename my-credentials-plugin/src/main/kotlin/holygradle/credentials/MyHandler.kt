package holygradle.credentials

import holygradle.custom_gradle.plugin_apis.CredentialSource
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.script.lang.kotlin.getValue
import java.io.ByteArrayOutputStream

class MyHandler(private val project: Project, private val credentialStorePath: String) : CredentialSource {
    companion object {
        fun defineExtension(project: Project, credentialStorePath: String): MyHandler {
            return if (project == project.rootProject) {
                project.extensions.create("my", MyHandler::class.java, project, credentialStorePath)
            } else {
                val my: MyHandler by project.rootProject.extensions
                project.extensions.add("my", my)
                my
            }
        }
    }

    private val separator = "&&&"
    private val defaultCredentialType = "Domain Credentials"
    private val credentialsCache = mutableMapOf<String, String>()
    val instructions: NamedDomainObjectContainer<InstructionsHandler> = project.container(InstructionsHandler::class.java)

    // This method returns userName&&&password (raw)
    private fun getCachedCredentials(credStorageKey: String): String? {
        var credStorageValue: String? = null
        if (credentialsCache.containsKey(credStorageKey)) {
            credStorageValue = credentialsCache[credStorageKey]
            project.logger.info("Requested credentials for '${credStorageKey}'. Found in cache.")
        } else {
            val credentialStoreOutput = ByteArrayOutputStream()
            val execResult = project.exec { spec ->
                spec.isIgnoreExitValue = true
                spec.commandLine(credentialStorePath, credStorageKey)
                spec.standardOutput = credentialStoreOutput
            }
            if (execResult.exitValue == 0) {
                credStorageValue = credentialStoreOutput.toString()
                credentialsCache[credStorageKey] = credStorageValue
                project.logger.info("Requested credentials for '${credStorageKey}'. Retrieved from Credential Manager")
            } else {
                project.logger.warn(
                    "WARNING: Requested credentials for '${credStorageKey}' from Credential Manager but failed"
                )
            }
        }
        return credStorageValue
    }
    
    private fun getCredentials(credentialType: String, forceAskUser: Boolean = false): Credentials {
        val usingLocalArtifacts: Boolean by project.extensions
        if (usingLocalArtifacts) {
            return Credentials("empty", "empty")
        }
        
        var username = System.getProperty("user.name").toLowerCase()
        val credStorageKey = getCredentialStorageKey(credentialType)
        val credStorageValue = getCachedCredentials(credStorageKey)

        if (forceAskUser || credStorageValue == null) {
            return getCredentialsFromUserAndStore(credentialType, username)
        }

        val credentials = credStorageValue.split(separator)
        username = credentials[0]
        val password = when (credentials.size) {
            1 -> ""
            2 -> credentials[1]
            else -> {
                project.logger.warn("WARNING: Attempting to get credentials from store, got more than 2 fields")
                username = credentials[1]
                credentials[2]
            }
        }

        return Credentials(username, password)
    }
    
    private fun getCredentialStorageKey(credentialType: String): String = "Intrepid - ${credentialType}"
    
    private fun getCredentialsFromUserAndStore(credentialType: String, currentUserName: String): Credentials {
        val my: MyHandler by project.extensions
        val instructionsHandler = my.instructions.findByName(credentialType)
        val instructionsText = instructionsHandler?.instructions

        val timeoutSeconds = 60 * 3
        val userCred = CredentialsForm.getCredentialsFromUser(credentialType, instructionsText, currentUserName, timeoutSeconds)
        if (userCred == null) {
            throw RuntimeException("User did not supply credentials '${credentialType}' within ${timeoutSeconds} seconds.")
        } else {
            val credStoreExe = credentialStorePath
            val credStorageKey = getCredentialStorageKey(credentialType)
            val credStorageValue = "${userCred.userName}${separator}${userCred.password}"
            credentialsCache[credStorageKey] = credStorageValue
            val result = project.exec { spec ->
                spec.isIgnoreExitValue = true
                spec.commandLine(credStoreExe, credStorageKey, userCred.userName, userCred.password)
                spec.standardOutput = ByteArrayOutputStream()
            }
            if (result.exitValue != 0) {
                project.logger.error(
                        "ERROR: Got exit code ${result.exitValue} while running ${credStoreExe} to store credentials."
                )
                project.logger.error("Maybe you need to install the x86 Microsoft Visual C++ Runtime?")
                throw RuntimeException("Failed to store credentials.")
            }
            println("Saved '${credentialType}' credentials for user '${userCred.userName}'.")
            return userCred
        }
    }

    override val username: String
        get() = username(defaultCredentialType)
    override val password: String
        get() = password(defaultCredentialType)
    
    fun username(credentialType: String): String = getCredentials(credentialType).userName
    fun password(credentialType: String): String = getCredentials(credentialType).password
}
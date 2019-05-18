package holygradle.credentials

import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.custom_gradle.plugin_apis.CredentialStore
import holygradle.custom_gradle.plugin_apis.Credentials
import holygradle.custom_gradle.plugin_apis.DEFAULT_CREDENTIAL_TYPE
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import holygradle.kotlin.dsl.getValue
import java.io.ByteArrayOutputStream

open class MyHandler(private val project: Project) : CredentialSource {
    companion object {
        @JvmStatic
        fun defineExtension(project: Project): MyHandler {
            return if (project == project.rootProject) {
                project.extensions.create("my", MyHandler::class.java, project)
            } else {
                val my: MyHandler by project.rootProject.extensions
                project.extensions.add("my", my)
                my
            }
        }
    }

    override val credentialStore: CredentialStore = WindowsCredentialStore()
    private val credentialsCache = mutableMapOf<String, Credentials?>()
    val instructions: NamedDomainObjectContainer<InstructionsHandler> = project.container(InstructionsHandler::class.java)

    // This method returns userName&&&password (raw)
    private fun getCachedCredentials(credStorageKey: String): Credentials? {
        project.logger.debug("getCachedCredentials(${credStorageKey}) START: cache = ${credentialsCache}")

        var credStorageValue: Credentials? = credentialsCache[credStorageKey]
        if (credStorageValue != null) {
            project.logger.info("Requested credentials for '${credStorageKey}'. Found in cache.")
        } else {
            credStorageValue = credentialStore.readCredential(credStorageKey)
            credentialsCache[credStorageKey] = credStorageValue
            project.logger.info("Requested credentials for '${credStorageKey}'. Retrieved from Credential Manager")
        }

        project.logger.debug("getCachedCredentials(${credStorageKey}) END: credStorageValue = ${credStorageValue}, " +
                "cache = ${credentialsCache}")

        return credStorageValue
    }
    
    private fun getCredentials(credentialType: String, forceAskUser: Boolean = false): Credentials {
        val usingLocalArtifacts: Boolean = project.property("usingLocalArtifacts") as Boolean
        if (usingLocalArtifacts) {
            return Credentials("empty", "empty")
        }

        val defaultUsername = System.getProperty("user.name").toLowerCase()
        val credStorageKey = getCredentialStorageKey(credentialType)
        return getCachedCredentials(credStorageKey)
                ?: getCredentialsFromUserAndStore(credentialType, defaultUsername)
    }
    
    private fun getCredentialStorageKey(credentialType: String): String = "Intrepid - ${credentialType}"
    
    private fun getCredentialsFromUserAndStore(credentialType: String, currentUserName: String): Credentials {
        val my: MyHandler by project.extensions
        val instructionsHandler = my.instructions.findByName(credentialType)
        val title = "Intrepid - ${credentialType}"
        val timeoutSeconds = 60 * 3
        val userCred =
                CredentialsForm.getCredentialsFromUser(title, instructionsHandler?.instructions, currentUserName, timeoutSeconds)
        if (userCred == null) {
            throw RuntimeException("User did not supply credentials '${credentialType}' within ${timeoutSeconds} seconds.")
        } else {
            val credStorageKey = getCredentialStorageKey(credentialType)
            credentialStore.writeCredential(credStorageKey, userCred)
            println("Saved '${credentialType}' credentials for user '${userCred.username}'.")
            return userCred
        }
    }

    override val username: String?
        get() = username(DEFAULT_CREDENTIAL_TYPE)
    override val password: String?
        get() = password(DEFAULT_CREDENTIAL_TYPE)
    
    fun username(credentialType: String): String? = getCredentials(credentialType).username
    fun password(credentialType: String): String? = getCredentials(credentialType).password
}
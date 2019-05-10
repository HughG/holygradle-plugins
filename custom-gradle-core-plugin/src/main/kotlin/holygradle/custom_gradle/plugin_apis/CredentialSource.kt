package holygradle.custom_gradle.plugin_apis

val DEFAULT_CREDENTIAL_TYPE = "Domain Credentials"

/**
 * An interface for plugins which can supply a username and password for authentication.
 */
interface CredentialSource {
    val credentialStore: CredentialStore

    /**
     * Returns a username for authentication.
     * @return A username for authentication.
     */
    val username: String

    /**
     * Returns a password for authentication.
     * @return A password for authentication.
     */
    val password: String
}


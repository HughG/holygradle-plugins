package holygradle.custom_gradle.plugin_apis

interface CredentialStore {
    fun readCredential(key: String): Credentials?
    fun writeCredential(key: String, credentials: Credentials)
}
package holygradle.custom_gradle.plugin_apis

interface CredentialStore {
    Credentials readCredential(String key)
    Credentials writeCredential(String key, Credentials credentials)
}
package holygradle.credentials

import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.custom_gradle.plugin_apis.CredentialStore
import holygradle.custom_gradle.plugin_apis.Credentials
import org.gradle.api.Project

class MyHandler implements CredentialSource {
    private final Project project
    private final CredentialStore credentialStore = new WindowsCredentialStore()
    private Map<String, Credentials> credentialsCache = [:]

    public static MyHandler defineExtension(Project project) {
        MyHandler myExtension

        if (project == project.rootProject) {
            myExtension = project.extensions.create("my", MyHandler, project)
            project.my.extensions.instructions = project.container(InstructionsHandler) { String instructionName ->
                project.my.extensions.create(instructionName, InstructionsHandler, instructionName)
            }
        } else {
            myExtension = project.rootProject.extensions.findByName("my") as MyHandler
            project.extensions.add("my", myExtension)
        }

        myExtension
    }

    public MyHandler(Project project) {
        this.project = project
    }

    @Override
    public CredentialStore getCredentialStore() {
        return credentialStore
    }

    private Credentials getCachedCredentials(String credStorageKey) {
        project.logger.debug "getCachedCredentials(${credStorageKey}) START: cache = ${credentialsCache}"

        Credentials credStorageValue
        if (credentialsCache.containsKey(credStorageKey)) {
            credStorageValue = credentialsCache[credStorageKey]
            project.logger.info("Requested credentials for '${credStorageKey}'. Found in cache.")
        } else {
            credStorageValue = credentialStore.readCredential(credStorageKey)
            if (credStorageValue != null) {
                credentialsCache[credStorageKey] = credStorageValue
                project.logger.info("Requested credentials for '${credStorageKey}'. Retrieved from Credential Manager")
            }
        }

        project.logger.debug "getCachedCredentials(${credStorageKey}) END: credStorageValue = ${credStorageValue}, " +
             "cache = ${credentialsCache}"

        credStorageValue
    }

    private Credentials getCredentials(String credentialType) {
        if (project.usingLocalArtifacts) {
            return new Credentials("empty", "empty")
        }

        String defaultUsername = System.getProperty("user.name").toLowerCase()
        String credStorageKey = getCredentialStorageKey(credentialType)
        Credentials credentials = getCachedCredentials(credStorageKey)

        if (credentials == null) {
            credentials = getCredentialsFromUserAndStore(credentialType, defaultUsername)
        }

        return credentials
    }

    private static String getCredentialStorageKey(String credentialType) {
        "Intrepid - ${credentialType}"
    }

    private Credentials getCredentialsFromUserAndStore(String credentialType, String currentUserName) {
        Collection<String> instructions = ["Please provide details for '${credentialType}'"]*.toString()
        final String title = "Intrepid - ${credentialType}".toString()
        Credentials userCred = CredentialsForm.getCredentialsFromUser(title, instructions, currentUserName, 60 * 3)
        if (userCred == null) {
            println "No change to credentials '${credentialType}'."
            return null
        } else {
            String credStorageKey = getCredentialStorageKey(credentialType)
            credentialStore.writeCredential(credStorageKey, userCred)
            println "Saved '${credentialType}' credentials for user '${userCred.userName}'."
            return userCred
        }
    }

    @Override
    public String getUsername() {
        username(DEFAULT_CREDENTIAL_TYPE)
    }

    @Override
    public String getPassword() {
        password(DEFAULT_CREDENTIAL_TYPE)
    }

    @Override
    public String username(String credentialType) {
        getCredentials(credentialType)?.userName
    }

    @Override
    public String password(String credentialType) {
        getCredentials(credentialType)?.password
    }
}
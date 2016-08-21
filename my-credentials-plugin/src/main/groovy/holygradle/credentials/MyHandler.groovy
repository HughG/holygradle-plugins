package holygradle.credentials

import holygradle.custom_gradle.plugin_apis.CredentialSource
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class MyHandler implements CredentialSource {
    private final Project project
    private final String credentialStorePath
    private final String separator = "&&&"
    private final String defaultCredentialType = "Domain Credentials"
    private Map<String,String> credentialsCache = [:]
    
    public static MyHandler defineExtension(Project project, String credentialStorePath) {
        MyHandler myExtension

        if (project == project.rootProject) {
            myExtension = project.extensions.create("my", MyHandler, project, credentialStorePath)
            project.my.extensions.instructions = project.container(InstructionsHandler) { String instructionName ->
                project.my.extensions.create(instructionName, InstructionsHandler, instructionName)
            }
        } else {
            myExtension = project.rootProject.extensions.findByName("my") as MyHandler
            project.extensions.add("my", myExtension)
        }
        
        myExtension
    }
    
    public MyHandler(Project project, String credentialStorePath) {
        this.project = project
        this.credentialStorePath = credentialStorePath
    }
        
    // This method returns username&&&password (raw)
    private String getCachedCredentials(String credStorageKey) {
        String credStorageValue = null
        if (credentialsCache.containsKey(credStorageKey)) {
            credStorageValue = credentialsCache[credStorageKey]
            project.logger.info("Requested credentials for '${credStorageKey}'. Found in cache.")
        } else {
            String credStoreExe = credentialStorePath
            OutputStream credentialStoreOutput = new ByteArrayOutputStream()
            ExecResult execResult = project.exec { ExecSpec spec ->
                spec.setIgnoreExitValue true
                spec.commandLine credStoreExe, credStorageKey
                spec.setStandardOutput credentialStoreOutput
            }
            if (execResult.getExitValue() == 0) {
                credStorageValue = credentialStoreOutput.toString()
                credentialsCache[credStorageKey] = credStorageValue
                project.logger.info("Requested credentials for '${credStorageKey}'. Retrieved from Credential Manager")
            } else {
                project.logger.warn(
                    "WARNING: Requested credentials for '${credStorageKey}' from Credential Manager but failed"
                )
            }
        }
        credStorageValue
    }
    
    private Credentials getCredentials(String credentialType, boolean forceAskUser = false) {
        if (project.usingLocalArtifacts) {
            return new Credentials("empty", "empty")
        }
        
        String username = System.getProperty("user.name").toLowerCase()
        String password
        String credStorageKey = getCredentialStorageKey(credentialType)
        String credStorageValue = getCachedCredentials(credStorageKey)

        if (forceAskUser || credStorageValue == null) {
            return getCredentialsFromUserAndStore(credentialType, username)
        }

        String[] credentials = credStorageValue.split(separator)
        username = credentials[0]
        if (credentials.size() == 3) {
            project.logger.info("Warning: Attempting to get credentials from store, got 3 fields")
            username = credentials[1]
            password = credentials[2]
        } else if (credentials.size() == 1) {
            password = ""
        } else {
            password = credentials[1]
        }

        return new Credentials(username, password)
    }
    
    private static String getCredentialStorageKey(String credentialType) {
        "Intrepid - ${credentialType}"
    }
    
    private Credentials getCredentialsFromUserAndStore(String credentialType, String currentUserName) {
        InstructionsHandler instructionsHandler = project.my.instructions.findByName(credentialType)
        Collection<String> instructions = null
        if (instructionsHandler != null) {
            instructions = instructionsHandler.getInstructions()
        }
        Credentials userCred = CredentialsForm.getCredentialsFromUser(credentialType, instructions, currentUserName, 60 * 3)
        if (userCred == null) {
            println "No change to credentials '${credentialType}'."
            return null
        } else {
            String credStoreExe = credentialStorePath
            String credStorageKey = getCredentialStorageKey(credentialType)
            GString credStorageValue = "${userCred.userName}${separator}${userCred.password}"
            credentialsCache[credStorageKey] = credStorageValue
            ExecResult result = project.exec { ExecSpec spec ->
                spec.setIgnoreExitValue true
                spec.commandLine credStoreExe, credStorageKey, userCred.userName, userCred.password
                spec.setStandardOutput new ByteArrayOutputStream()
            }
            if (result.getExitValue() != 0) {
                println "Got exit code ${result.getExitValue()} while running ${credStoreExe} to store credentials."
                println "Maybe you need to install the x86 CRT?"
                throw new RuntimeException("Failed to store credentials.")
            }
            println "Saved '${credentialType}' credentials for user '${userCred.userName}'."
            return userCred
        }
    }

    public String getUsername() {
        username(defaultCredentialType)
    }
    
    public String getPassword() {
        password(defaultCredentialType)
    }
    
    public String username(String credentialType) {
        getCredentials(credentialType)?.userName
    }
    
    public String password(String credentialType) {
        getCredentials(credentialType)?.password
    }
}
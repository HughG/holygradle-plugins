package holygradle.credentials

import org.gradle.*
import org.gradle.api.*

class MyHandler {
    private final Project project
    private final String credentialStorePath
    private final String separator = "&&&"
    private final String defaultCredentialType = "Domain Credentials"
    private String userPassword = null
    private def allCredentialTypes = new HashSet<String>()
    private def cleanCredentialTypes = new HashSet<String>()
    private def credentialsCache = [:]
    
    public static def defineExtension(Project project, String credentialStorePath) {
        def myExtension = null
        
        if (project == project.rootProject) {
            myExtension = project.extensions.create("my", MyHandler, project, credentialStorePath)
            project.my.extensions.instructions = project.container(InstructionsHandler) { instructionName ->
                project.packageArtifacts.extensions.create(instructionName, InstructionsHandler, instructionName)
            }
        } else {
            myExtension = project.extensions.add("my", project.rootProject.extensions.findByName("my"))
        }
        
        myExtension
    }
    
    public MyHandler(Project project, String credentialStorePath) {
        this.project = project
        this.credentialStorePath = credentialStorePath
    }
        
    private String getCachedCredentials(String credStorageKey) {
        String credStorageValue = null
        if (credentialsCache.containsKey(credStorageKey)) {
            credStorageValue = credentialsCache[credStorageKey]
            project.logger.info("Requested credentials for '${credStorageKey}'. Found in cache.")
        } else {
            def credStoreExe = credentialStorePath
            def credentialStoreOutput = new ByteArrayOutputStream()
            def execResult = project.exec {
                setIgnoreExitValue true
                commandLine credStoreExe, credStorageKey
                setStandardOutput credentialStoreOutput
            }
            if (execResult.getExitValue() == 0) {
                credStorageValue = credentialStoreOutput.toString()
                credentialsCache[credStorageKey] = credStorageValue
                project.logger.info("Requested credentials for '${credStorageKey}'. Retrieved from Credential Manager")
            }
        }
        credStorageValue
    }
    
    private def getCredentials(String credentialType) {
        getCredentials(credentialType, false)
    }
    
    private def getCredentials(String credentialType, boolean forceAskUser) {
        if (project.ext.usingLocalArtifacts) {
            return ["empty", "empty"]
        }
        allCredentialTypes.add(credentialType)
        
        def username = System.getProperty("user.name").toLowerCase()
        def credStorageKey = getCredentialStorageKey(credentialType)
        
        def credentials = null
        def credStorageValue = getCachedCredentials(credStorageKey)
        if (credStorageValue != null) {
            credentials = credStorageValue.split(separator)
            username = credentials[0]
            if (credentials.size() == 3) {
                credentials = [credentials[1], credentials[2]]
            }
            if (credentials.size() == 1) {
                credentials = [credentials[0], ""]
            }
        } 
        
        if (forceAskUser || credentials == null) {
            credentials = getCredentialsFromUserAndStore(credentialType, username)
        }
        
        credentials
    }
    
    private String getCredentialStorageKey(String credentialType) {
        "Intrepid - ${credentialType}"
    }
    
    private def getCredentialsFromUserAndStore(String credentialType, String currentUserName) {
        def instructionsHandler = project.my.instructions.findByName(credentialType)
        def instructions = null
        if (instructionsHandler != null) {
            instructions = instructionsHandler.getInstructions()
        }
        def userCred = CredentialsForm.getCredentialsFromUser(credentialType, instructions, currentUserName, 60 * 3)
        if (userCred == null) {
            println "No change to credentials '${credentialType}'."
            return null
        } else {
            def credStoreExe = credentialStorePath
            def credStorageKey = getCredentialStorageKey(credentialType)
            def credentials = [userCred.getKey(), userCred.getValue()]
            def credStorageValue = "${userCred.getKey()}${separator}${userCred.getValue()}"
            credentialsCache[credStorageKey] = credStorageValue
            def result = project.exec {
                setIgnoreExitValue true
                commandLine credStoreExe, credStorageKey, userCred.getKey(), userCred.getValue()
                setStandardOutput new ByteArrayOutputStream()
            }
            if (result.getExitValue() != 0) {
                println "Got exit code ${result.getExitValue()} while running ${credStoreExe} to store credentials."
                println "Maybe you need to install the x86 CRT?"
                throw new RuntimeException("Failed to store credentials.")
            }
            println "Saved '${credentialType}' credentials for user '${userCred.getKey()}'."
            cleanCredentialTypes.add(credentialType)
            return credentials
        }
    }

    public String username() {
        username(defaultCredentialType)
    }
    
    public String password() {
        password(defaultCredentialType)
    }
    
    public String username(String credentialType) {
        def cred = getCredentials(credentialType)
        (cred == null) ? null : cred[0]
    }
    
    public String password(String credentialType) {
        def cred = getCredentials(credentialType)
        (cred == null) ? null : cred[1]
    }
}
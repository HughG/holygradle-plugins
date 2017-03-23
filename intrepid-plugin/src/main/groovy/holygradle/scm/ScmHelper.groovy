package holygradle.scm

import org.gradle.api.logging.Logger
import org.gradle.process.ExecSpec

class ScmHelper {
    public static String getGitConfigValue(Command gitCommand, File workingDir, String configKey) {
        return gitCommand.execute({ ExecSpec spec ->
            spec.workingDir = workingDir
            spec.args "config", "--get", configKey
        }, {int error_code ->
            // Error code 1 means the section or key is invalid, probably just no remote set, so don't throw.
            return (error_code != 1)
        })
    }

    public static void storeCredential(
        Logger logger,
        Command credentialStoreCommand,
        String credentialName,
        String username,
        String password
    ) {
        credentialStoreCommand.execute({ ExecSpec spec ->
            spec.args credentialName, username, password
        }, { int exitValue ->
            logger.debug "  credential-store.exe exit value: ${exitValue}"
            if (exitValue == -1073741515) {
                logger.info "-" * 80
                logger.info "Failed to cache credentials. This is probably because you don't have " +
                    "the Visual C++ 2010 Redistributable installed on your machine. Please download and install the " +
                    "x86 version before continuing, from"
                logger.info "    http://www.microsoft.com/download/en/details.aspx?id=5555"
                logger.info "-" * 80
                return false
            }
            return true
        })

        logger.info "  Cached credential '${credentialName}'."
    }

    public static boolean repoSupportsAuthentication(String repoUrl) {
        return new URL(repoUrl).protocol in ["http", "https"]
    }
}

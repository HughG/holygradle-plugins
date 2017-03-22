package holygradle.scm

import org.gradle.process.ExecSpec

class GitHelper {
    public static String getConfigValue(Command gitCommand, File workingDir, String configKey) {
        return gitCommand.execute({ ExecSpec spec ->
            spec.workingDir = workingDir
            spec.args "config", "--get", configKey
        }, {int error_code ->
            // Error code 1 means the section or key is invalid, probably just no remote set, so don't throw.
            return (error_code != 1)
        })
    }
}

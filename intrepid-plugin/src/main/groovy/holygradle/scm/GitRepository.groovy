package holygradle.scm

import org.gradle.process.ExecSpec

class GitRepository implements SourceControlRepository {
    private final File workingCopyDir
    private final Command gitCommand

    public GitRepository(Command gitCommand, File workingCopyDir) {
        this.gitCommand = gitCommand
        this.workingCopyDir = workingCopyDir
    }

    public File getLocalDir() {
        workingCopyDir.absoluteFile
    }

    public String getProtocol() {
        "git"
    }

    public String getUrl() {
        // May need to strip "username[:password]@" from URL.
        File localWorkingCopyDir = workingCopyDir // capture private for closure
        String command_result = gitCommand.execute({ ExecSpec spec ->
            spec.workingDir = localWorkingCopyDir
            spec.args(
                    "config",
                    "--get",
                    "remote.origin.url"
            )
        }, {int error_code ->
            return (error_code != 1)
        })
        return command_result
    }

    public String getRevision() {
        File localWorkingCopyDir = workingCopyDir // capture private for closure
        return gitCommand.execute { ExecSpec spec ->
            spec.workingDir = localWorkingCopyDir
            spec.args(
                "rev-parse", // Execute the "rev-parse" command,
                "HEAD" // asking for only the node, not branch/tag info.
            )
        }
    }

    public boolean hasLocalChanges() {
        // Execute git status with added, removed or modified files
        File localWorkingCopyDir = workingCopyDir // capture private for closure
        String changes = gitCommand.execute { ExecSpec spec ->
            spec.workingDir = localWorkingCopyDir
            spec.args "status", "--porcelain", "--untracked-files=no"
        }
        changes.trim().length() > 0
    }
}

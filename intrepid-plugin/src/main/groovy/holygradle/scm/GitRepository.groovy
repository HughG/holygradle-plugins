package holygradle.scm

import org.gradle.process.ExecSpec

import java.nio.file.Files

class GitRepository extends SourceControlRepositoryBase {
    public static SourceControlType TYPE = new Type()

    public GitRepository(Command scmCommand, File workingCopyDir) {
        super(scmCommand, workingCopyDir)
    }

    public String getProtocol() {
        "git"
    }

    public String getUrl() {
        // May need to strip "username[:password]@" from URL.
        return ScmHelper.getGitConfigValue(scmCommand, workingCopyDir, "remote.origin.url")
    }

    public String getRevision() {
        File localWorkingCopyDir = workingCopyDir // capture private for closure
        return scmCommand.execute { ExecSpec spec ->
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
        String changes = scmCommand.execute { ExecSpec spec ->
            spec.workingDir = localWorkingCopyDir
            spec.args "status", "--porcelain", "--untracked-files=no"
        }
        changes.trim().length() > 0
    }

    protected boolean ignoresFileInternal(File file) {
        int exitValue = 0
        scmCommand.execute({ ExecSpec spec ->
            spec.workingDir = workingCopyDir
            spec.args "check-ignore", "-q", file.absolutePath
        }, {
            exitValue = it
            return (it != 0 && it != 1) // 0 = is ignored, 1 = is NOT ignored, 128 (or other) = error
        })
        return (exitValue == 0)
    }

    private static class Type implements SourceControlType {
        @Override
        String getStateDirName() {
            return ".git"
        }

        @Override
        String getExecutableName() {
            return "git"
        }

        @Override
        Class<SourceControlRepository> getRepositoryClass() {
            return GitRepository.class
        }
    }
}

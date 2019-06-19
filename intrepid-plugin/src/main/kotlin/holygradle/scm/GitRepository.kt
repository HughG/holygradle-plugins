package holygradle.scm

import org.gradle.api.Action
import org.gradle.process.ExecSpec
import java.io.File
import java.util.function.Predicate

internal class GitRepository(
        scmCommand: Command,
        workingCopyDir: File
) : SourceControlRepositoryBase(scmCommand, workingCopyDir) {
    companion object {
        @JvmStatic
        val TYPE = object : SourceControlType {
            override val executableName: String = "git"
            override val repositoryClass: Class<GitRepository> = GitRepository::class.java
            override val stateDirName: String = ".git"
        }
    }

    override val protocol: String = "git"

    override val url: String
        get() {
            // May need to strip "username[:password]@" from URL.
            return ScmHelper.getGitConfigValue(scmCommand, workingCopyDir, "remote.origin.url")
        }

    override val revision: String?
        get() {
            return scmCommand.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args(
                    "rev-parse", // Execute the "rev-parse" command,
                    "HEAD" // asking for only the node, not branch/tag info.
                )
            })
        }

    override val hasLocalChanges: Boolean
        get() {
            // Execute git status with added, removed or modified files
            val changes = scmCommand.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args("status", "--porcelain", "--untracked-files=no")
            })
            return changes.trim().isNotEmpty()
        }

    override fun ignoresFileInternal(file: File): Boolean {
        var exitValue = 0
        scmCommand.execute(Action { spec ->
            spec.workingDir = workingCopyDir
            spec.args("check-ignore", "-q", file.absolutePath)
        }, Predicate {
            exitValue = it
            (it != 0 && it != 1) // 0 = is ignored, 1 = is NOT ignored, 128 (or other) = error
        })
        return (exitValue == 0)
    }
}

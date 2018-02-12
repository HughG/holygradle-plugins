package holygradle.scm

import org.gradle.api.Action
import java.io.File

class GitRepository(val command: Command, localDir: File) : SourceControlRepository {
    override val localDir: File = localDir.absoluteFile
    override val protocol: String = "git"

    override val url: String
        get() {
            // May need to strip "username[:password]@" from URL.
            val result = command.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args(
                        "config",
                        "--get",
                        "remote.origin.url"
                )
            }, { errorCode ->
                // Error code 1 means the section or key is invalid, probably just no remote set, so don't throw.
                (errorCode != 1)
            })
            return result
        }

    override val revision: String?
        get() {
            return command.execute(Action { spec ->
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
            val changes = command.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args("status", "--porcelain", "--untracked-files=no")
            })
            return changes.trim().isNotEmpty()
        }
}

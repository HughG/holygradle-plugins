package holygradle.scm

import org.gradle.api.Action
import java.io.File

class HgRepository(val command: Command, localDir: File) : SourceControlRepository {
    override val localDir: File = localDir.absoluteFile
    override val protocol: String = "hg"

    override val url: String
        get() {
            // TODO 2016-04-15 HughG: Instead of this, run "hg paths default" and capture the output.
            // May need to strip "username[:password]@" from URL.
            val hgrc = File(localDir, "/.hg/hgrc")
            var url = "unknown"
            if (hgrc.exists()) {
                val urlRegex = "URL: (\\S+)".toRegex()
                for (it in hgrc.readLines()) {
                    val groups = urlRegex.find(it)?.groupValues
                    if (groups != null && groups.size == 2) {
                        url = groups[1]
                        break
                    }
                }
            }
            return url
        }

    override val revision: String?
        get() {
            // Use "hg id" to get the node (in its "shortest possible string") form, then use "hg log"
            // to convert to the full node string.  The node may have a trailing "+" if the working
            // copy is modified, which we will remove.
    
            var minimalNode = command.execute(Action { spec ->
                    spec.workingDir = localDir
                    spec.args(
                        "id", // Execute the "id" command,
                        "-i" // asking for only the node, not branch/tag info.
                    )
                })
            if (minimalNode.endsWith("+")) {
                minimalNode = minimalNode.dropLast(1)
            }
    
            return command.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args(
                    "log",                      // Execute log command,
                    "-r", minimalNode,          // pointing at the revision of the working copy,
                    "-l", "1",                  // limiting the results to 1,
                    "--template", "\"{node}\""  // formatting the results to get the changeset hash.
                )
            })
        }

    override val hasLocalChanges: Boolean
        get() {
            // Execute hg status with added, removed or modified files
            val changes = command.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args("status", "-amrdC")
            })
            return changes.trim().isNotEmpty()
        }
}

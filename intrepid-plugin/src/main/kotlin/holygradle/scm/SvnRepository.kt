package holygradle.scm

import org.gradle.api.Action
import java.io.File

class SvnRepository(val command: Command, override val localDir: File) : SourceControlRepository {
    override val protocol: String = "svn"

    override val url: String
        get() {
            var url = "unknown"
            val info = command.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args("info")
            })
            val groups = "URL: (\\S+)".toRegex().find(info)?.groupValues
            if (groups != null && groups.size == 2) {
                url = groups[1]
            }
            return url
        }

    override val revision: String?
        get() {
            var revision = "unknown"
            val info = command.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args("info")
            })
            val groups = "Revision: (\\d+)".toRegex().find(info)?.groupValues
            if (groups != null && groups.size == 2) {
                revision = groups[1]
            }
            return revision
        }

    override val hasLocalChanges: Boolean
        get() {
            val changes = command.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args("status", "--quiet", "--ignore-externals")
            })
            return changes.trim().isNotEmpty()
        }
}
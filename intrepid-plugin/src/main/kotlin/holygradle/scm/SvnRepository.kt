package holygradle.scm

import org.gradle.api.Action
import java.io.File

class SvnRepository(scmCommand: Command, workingCopyDir: File) : SourceControlRepositoryBase(scmCommand, workingCopyDir) {
    companion object {
        @JvmStatic
        val TYPE = object : SourceControlType {
            override val executableName: String = "svn  "
            override val repositoryClass: Class<SvnRepository> = SvnRepository::class.java
            override val stateDirName: String = ".svn"
        }
    }

    override val protocol: String = "svn"

    override val url: String
        get() {
            var url = "unknown"
            val info = scmCommand.execute(Action { spec ->
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
            val info = scmCommand.execute(Action { spec ->
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
            val changes = scmCommand.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args("status", "--quiet", "--ignore-externals")
            })
            return changes.trim().isNotEmpty()
        }

    override fun ignoresFileInternal(file: File): Boolean {
        // If the file is explicitly ignored, "svn status" will output a line starting with "I".  However, if it's
        // somewhere under an ignored folder, then "svn status" for any file under that folder will output nothing on
        // standard output (and a warning on standard error: "svn: warning: W155010: The node 'filename' was not found"),
        // in which case we keep looking upward until we hit the working copy root (which we don't check, because a repo
        // which ignores its root folder seems like madness!).
        var maybeIgnoredFile = file.absoluteFile
        while (maybeIgnoredFile != workingCopyDir) {
            val status = scmCommand.execute(Action { spec ->
                spec.workingDir = workingCopyDir
                spec.args("status", maybeIgnoredFile.toString())
            })
            if (status == "") {
                maybeIgnoredFile = maybeIgnoredFile.parentFile
            } else {
                return status.startsWith("I")
            }
        }
        return false
    }
}
package holygradle.scm

import org.gradle.api.Action
import org.gradle.process.ExecSpec
import java.io.File
import java.net.URL
import java.util.function.Predicate

class HgRepository(scmCommand: Command, workingCopyDir: File) : SourceControlRepositoryBase(scmCommand, workingCopyDir) {
    companion object {
        @JvmStatic
        val TYPE = object : SourceControlType {
            override val executableName: String = "hg"
            override val repositoryClass: Class<HgRepository> = HgRepository::class.java
            override val stateDirName: String = ".hg"
        }
    }

    override val protocol: String = "hg"

    override val url: String
        get() {
            var savedSpec: ExecSpec? = null
            val defaultPath = scmCommand.execute(Action { spec ->
                savedSpec = spec // so we can access the configured error stream
                spec.workingDir = workingCopyDir
                spec.args(
                        "paths", // Execute the "paths" command,
                        "default" // asking for the default push target
                )
            }, Predicate { exitValue ->
                val errorString = savedSpec!!.errorOutput.toString().replace("[\\r\\n]".toRegex(), "")
                val defaultRemoteNotFound = (exitValue == 1) && (errorString == "not found!")
                // Throw if there's a non-zero exit value, unless it's just because there's no default remote,
                // which is perfectly valid for a new, never-pushed repo.
                (exitValue == 0) || !defaultRemoteNotFound
            }
            )
            if (defaultPath.trim().isEmpty()) {
                return "unknown"
            }
            val defaultUrl = URL(defaultPath)
            val defaultUrlWithoutUserInfo = URL(
                    defaultUrl.protocol,
                    defaultUrl.host,
                    defaultUrl.port,
                    defaultUrl.file
            )
            return defaultUrlWithoutUserInfo.toString()
        }

    override val revision: String?
        get() {
            // Use "hg id" to get the node (in its "shortest possible string") form, then use "hg log"
            // to convert to the full node string.  The node may have a trailing "+" if the working
            // copy is modified, which we will remove.
    
            var minimalNode = scmCommand.execute(Action { spec ->
                    spec.workingDir = localDir
                    spec.args(
                        "id", // Execute the "id" command,
                        "-i" // asking for only the node, not branch/tag info.
                    )
                })
            if (minimalNode.endsWith("+")) {
                minimalNode = minimalNode.dropLast(1)
            }
    
            return scmCommand.execute(Action { spec ->
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
            val changes = scmCommand.execute(Action { spec ->
                spec.workingDir = localDir
                spec.args("status", "-amrdC")
            })
            return changes.trim().isNotEmpty()
        }

    override fun ignoresFileInternal(file: File): Boolean {
        val ignoredFileLines = scmCommand.execute(Action { spec ->
            spec.workingDir = workingCopyDir
            spec.args("status", "-i", file.absolutePath)
        }).lines()
        if (ignoredFileLines.isEmpty()) {
            return false
        }
        val line = ignoredFileLines[0]
        val fileRelativePath = workingCopyDir.toPath().relativize(file.toPath())
        // We do a startsWith check (instead of ==) because "file" might be a directory, in which case we'll get the
        // filename of the first file in the folder.
        return (line == "I $fileRelativePath")
    }
}

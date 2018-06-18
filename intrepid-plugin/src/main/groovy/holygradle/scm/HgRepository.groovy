package holygradle.scm

import org.gradle.process.ExecSpec

class HgRepository implements SourceControlRepository {
    private final File workingCopyDir
    private final Command hgCommand

    public HgRepository(Command hgCommand, File workingCopyDir) {
        this.hgCommand = hgCommand
        this.workingCopyDir = workingCopyDir
    }

    public File getLocalDir() {
        workingCopyDir.absoluteFile
    }
    
    public String getProtocol() {
        "hg"
    }
    
    public String getUrl() {
        File localWorkingCopyDir = workingCopyDir // capture private for closure
        ExecSpec savedSpec = null
        String defaultPath = hgCommand.execute(
            { ExecSpec spec ->
                savedSpec = spec // so we can access the configured error stream
                spec.workingDir = localWorkingCopyDir
                spec.args(
                        "paths", // Execute the "paths" command,
                        "default" // asking for the default push target
                )
            },
            { int exitValue ->
                String errorString = savedSpec.errorOutput.toString().replaceAll('[\\r\\n]', '')
                boolean defaultRemoteNotFound = (exitValue == 1) && (errorString == 'not found!')
                // Throw if there's a non-zero exit value, unless it's just because there's no default remote,
                // which is perfectly valid for a new, never-pushed repo.
                return (exitValue == 0) || !defaultRemoteNotFound
            }
        )
        if (defaultPath.trim().isEmpty()) {
            return "unknown"
        }
        URL defaultUrl = new URL(defaultPath)
        URL defaultUrlWithoutUserInfo = new URL(
            defaultUrl.protocol,
            defaultUrl.host,
            defaultUrl.port,
            defaultUrl.file
        )
        return defaultUrlWithoutUserInfo.toString()
    }

    public String getRevision() {
        // Use "hg id" to get the node (in its "shortest possible string") form, then use "hg log"
        // to convert to the full node string.  The node may have a trailing "+" if the working
        // copy is modified, which we will remove.

        File localWorkingCopyDir = workingCopyDir // capture private for closure
        String minimalNode = hgCommand.execute { ExecSpec spec ->
                spec.workingDir = localWorkingCopyDir
                spec.args(
                    "id", // Execute the "id" command,
                    "-i" // asking for only the node, not branch/tag info.
                )
            }
        if (minimalNode.endsWith("+")) {
            minimalNode = minimalNode[0..-2] // Drop the last character.
        }

        return hgCommand.execute { ExecSpec spec ->
            spec.workingDir = localWorkingCopyDir
            spec.args(
                "log",                      // Execute log command,
                "-r", minimalNode,          // pointing at the revision of the working copy,
                "-l", "1",                  // limiting the results to 1,
                "--template", "\"{node}\""  // formatting the results to get the changeset hash.
            )
        }
    }
    
    public boolean hasLocalChanges() {
        // Execute hg status with added, removed or modified files
        File localWorkingCopyDir = workingCopyDir // capture private for closure
        String changes = hgCommand.execute { ExecSpec spec ->
            spec.workingDir = localWorkingCopyDir
            spec.args "status", "-amrdC"
        }
        changes.trim().length() > 0
    }
}

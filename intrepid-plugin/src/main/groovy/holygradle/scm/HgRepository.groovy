package holygradle.scm

import org.gradle.process.ExecSpec

import java.nio.file.Files

class HgRepository extends SourceControlRepositoryBase {
    public static SourceControlType TYPE = new Type()

    public HgRepository(Command scmCommand, File workingCopyDir) {
        super(scmCommand, workingCopyDir)
    }
    
    public String getProtocol() {
        "hg"
    }
    
    public String getUrl() {
        File localWorkingCopyDir = workingCopyDir // capture private for closure
        ExecSpec savedSpec = null
        String defaultPath = scmCommand.execute(
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
        String minimalNode = scmCommand.execute { ExecSpec spec ->
                spec.workingDir = localWorkingCopyDir
                spec.args(
                    "id", // Execute the "id" command,
                    "-i" // asking for only the node, not branch/tag info.
                )
            }
        if (minimalNode.endsWith("+")) {
            minimalNode = minimalNode[0..-2] // Drop the last character.
        }

        return scmCommand.execute { ExecSpec spec ->
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
        String changes = scmCommand.execute { ExecSpec spec ->
            spec.workingDir = localWorkingCopyDir
            spec.args "status", "-amrdC"
        }
        changes.trim().length() > 0
    }

    protected boolean ignoresFileInternal(File file) {
        List<String> ignoredFileLines = scmCommand.execute { ExecSpec spec ->
            spec.workingDir = workingCopyDir
            spec.args "status", "-i", file.absolutePath
        }.readLines()
        if (ignoredFileLines.isEmpty()) {
            return false
        }
        String line = ignoredFileLines[0]
        String fileRelativePath = workingCopyDir.toPath().relativize(file.toPath())
        // We do a startsWith check (instead of ==) because "file" might be a directory, in which case we'll get the
        // filename of the first file in the folder.
        return (line == "I " + fileRelativePath)
    }

    private static class Type implements SourceControlType {
        @Override
        String getStateDirName() {
            return ".hg"
        }

        @Override
        String getExecutableName() {
            return "hg"
        }

        @Override
        Class<SourceControlRepository> getRepositoryClass() {
            return HgRepository.class
        }
    }
}

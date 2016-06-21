package holygradle.scm

import org.gradle.process.ExecSpec

import java.util.regex.Matcher

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
        // TODO 2016-04-15 HughG: Instead of this, run "hg paths default" and capture the output.
        // May need to strip "username[:password]@" from URL.
        File hgrc = new File(workingCopyDir, "/.hg/hgrc")
        String url = "unknown"
        if (hgrc.exists()) {
            hgrc.text.eachLine {
                Matcher match = it =~ /default = (.+)/
                if (match.size() != 0) {
                    final List<String> matches = match[0] as List<String>
                    url = matches[1]
                }
                return
            }
        }
        url
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

package holygradle.scm

import org.gradle.process.ExecSpec

import java.util.regex.Matcher

class HgRepository implements SourceControlRepository {
    File workingCopyDir
    HgCommand hgCommand
    
    public HgRepository(HgCommand hgCommand, File workingCopyDir) {
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
        File hgrc = new File(workingCopyDir, "/.hg/hgrc")
        String url = "unknown"
        if (hgrc.exists()) {
            hgrc.text.eachLine {
                Matcher match = it =~ /default = (.+)/
                if (match.size() != 0) {
                    final List<String> matches = match[0] as List<String>
                    url = matches[1]
                }
            }
        }
        url
    }

    public String getRevision() {
        // Use "hg id" to get the node (in its "shortest possible string") form, then use "hg log"
        // to convert to the full node string.  The node may have a trailing "+" if the working
        // copy is modified, which we will remove.

        String minimalNode = hgCommand.execute { ExecSpec spec ->
                spec.workingDir = workingCopyDir
                spec.args(
                    "id", // Execute the "id" command,
                    "-i" // asking for only the node, not branch/tag info.
                )
            }
        if (minimalNode.endsWith("+")) {
            minimalNode = minimalNode[0..-2] // Drop the last character.
        }

        return hgCommand.execute { ExecSpec spec ->
            spec.workingDir = workingCopyDir
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
        String changes = hgCommand.execute { ExecSpec spec ->
            spec.workingDir = workingCopyDir
            spec.args "status", "-amrdC"
        }
        changes.trim().length() > 0
    }
}

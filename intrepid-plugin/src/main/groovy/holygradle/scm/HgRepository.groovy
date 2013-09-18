package holygradle.scm

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

        def args = [
            "log", "-l", "1",           // Execute log command, limiting the results to 1
            "--template", "\"{node}\""  // Filter the results to get the changeset hash
        ]
        return hgCommand.execute(args)
    }
    
    public boolean hasLocalChanges() {
        // Execute hg status with added, removed or modified files
        def changes = hgCommand.execute(["status", "-amrdC"])
        changes.trim().length() > 0
    }
}
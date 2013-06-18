package holygradle.scm

import com.aragost.javahg.Changeset
import com.aragost.javahg.Repository
import com.aragost.javahg.RepositoryConfiguration
import com.aragost.javahg.commands.StatusCommand
import com.aragost.javahg.commands.StatusResult

import java.util.regex.Matcher

class HgRepository implements SourceControlRepository {
    File workingCopyDir
    
    public HgRepository(File localPath) {
        workingCopyDir = localPath
    }

    public File getLocalDir() {
        workingCopyDir
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
        RepositoryConfiguration repoConf = RepositoryConfiguration.DEFAULT
        Repository repo = Repository.open(repoConf, workingCopyDir)
        Changeset changeset = repo.workingCopy().getParent1()
        String revision = changeset.getNode()
        repo.close()
        revision
    }
    
    public boolean hasLocalChanges() {
        RepositoryConfiguration repoConf = RepositoryConfiguration.DEFAULT
        Repository repo = Repository.open(repoConf, workingCopyDir)
        StatusCommand statusCommand = new StatusCommand(repo)
        StatusResult status = statusCommand.execute()
        int changes = 
            status.getModified().size() + 
            status.getAdded().size() + 
            status.getMissing().size() + 
            status.getClean().size() +
            status.getCopied().size()
        repo.close()
        changes > 0
    }
}
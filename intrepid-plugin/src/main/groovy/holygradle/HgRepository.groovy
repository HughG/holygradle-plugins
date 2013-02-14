package holygradle

import com.aragost.javahg.Changeset
import com.aragost.javahg.Repository
import com.aragost.javahg.RepositoryConfiguration
import com.aragost.javahg.commands.StatusCommand

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
        def hgrc = new File(workingCopyDir, "/.hg/hgrc")
        def url = "unknown"
        if (hgrc.exists()) {
            hgrc.text.eachLine {
                def match = it =~ /default = (.+)/
                if (match.size() != 0) {
                    url = match[0][1]
                }
            }
        }
        url
    }
    
    public String getRevision() {
        def repoConf = RepositoryConfiguration.DEFAULT
        Repository repo = Repository.open(repoConf, workingCopyDir)
        Changeset changeset = repo.workingCopy().getParent1()
        def revision = changeset.getNode()
        repo.close()
        revision
    }
    
    public boolean hasLocalChanges() {
        def repoConf = RepositoryConfiguration.DEFAULT
        Repository repo = Repository.open(repoConf, workingCopyDir)
        def statusCommand = new StatusCommand(repo)
        def status = statusCommand.execute()
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
package holygradle

import org.tmatesoft.svn.core.wc.SVNClientManager

class SvnRepository implements SourceControlRepository {
    File workingCopyDir
    
    public SvnRepository(File localPath) {
        workingCopyDir = localPath
    }
    
    public File getLocalDir() {
        workingCopyDir
    }
    
    public String getProtocol() {
        "svn"
    }
    
    public String getUrl() {
        def clientManager = SVNClientManager.newInstance();
        clientManager.getStatusClient().doStatus(workingCopyDir, false).getURL().toString()
    }
    
    public String getRevision() {
        def clientManager = SVNClientManager.newInstance();
        clientManager.getStatusClient().doStatus(workingCopyDir, false).getRevision().getNumber()
    }
    
    public boolean hasLocalChanges() {
        // TODO
        return false
    }
}
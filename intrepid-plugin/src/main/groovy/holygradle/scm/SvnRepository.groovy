package holygradle.scm

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
        SVNClientManager clientManager = SVNClientManager.newInstance();
        clientManager.getStatusClient().doStatus(workingCopyDir, false).getURL().toString()
    }
    
    public String getRevision() {
        SVNClientManager clientManager = SVNClientManager.newInstance();
        clientManager.getStatusClient().doStatus(workingCopyDir, false).getRevision().getNumber()
    }
    
    public boolean hasLocalChanges() {
        // TODO
        return false
    }
}
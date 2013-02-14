package holygradle

import org.tmatesoft.svn.core.wc.SVNClientManager

class SvnHelper {
    public static String getSourceControlRevision(File workingCopyDir) {
        def clientManager = SVNClientManager.newInstance();
        clientManager.getStatusClient().doStatus(workingCopyDir, false).getRevision().getNumber()
    }
}
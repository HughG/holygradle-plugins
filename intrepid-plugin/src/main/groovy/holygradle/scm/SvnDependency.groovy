package holygradle

import org.gradle.api.*
import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.wc.*
import org.tmatesoft.svn.core.internal.wc.*

class SvnDependency extends SourceDependency {
    public boolean export = false
    public boolean ignoreExternals = false
    
    public SvnDependency(Project project, SourceDependencyHandler sourceDependency) {
        super(project, sourceDependency)
    }
    
    @Override
    public String getFetchTaskDescription() {
        "Retrieves an SVN Checkout for '${sourceDependency.name}' into your workspace."
    }
    
    private def TryCheckout(def destinationFile, String repoUrl, String repoRevision, def svnConfigDir, def depth, def username, def password) {
        try {
            boolean storeCredentials = false
            if (username != null && password != null) {
                storeCredentials = true
            }
            
            def svnRevision = SVNRevision.HEAD
            if (repoRevision != null) {
                svnRevision = SVNRevision.create(Long.parseLong(repoRevision))
            }
            
            def svnUrl = SVNURL.parseURIEncoded(repoUrl)
            def authManager = new DefaultSVNAuthenticationManager(svnConfigDir, storeCredentials, username, password)                        
            def options = SVNWCUtil.createDefaultOptions(true)
            def clientManager = SVNClientManager.newInstance(options, authManager)
            def updateClient = clientManager.getUpdateClient()
            updateClient.setIgnoreExternals(ignoreExternals)
            if (sourceDependency.export) {
                updateClient.doExport(svnUrl, destinationFile, svnRevision, svnRevision, "\n", false, depth)
            } else {
                updateClient.doCheckout(svnUrl, destinationFile, svnRevision, svnRevision, depth, false)
            }
            clientManager.dispose()
            
            def writeVersion = sourceDependency.export
            if (sourceDependency.writeVersionInfoFile != null) {
                writeVersion = sourceDependency.writeVersionInfoFile
            }
            if (writeVersion) {
                writeVersionInfoFile()
            }
            return true
        } catch (SVNAuthenticationException e) {
            return false
        }
    }
    
    private def TryCheckout(def destinationDir, String repoUrl, String repoRevision, def svnConfigDir) {
        TryCheckout(destinationDir, repoUrl, repoRevision, svnConfigDir, SVNDepth.INFINITY, null, null)
    }

    private def getSvnConfigDir() {
        // Get SVN-config path.
        def svnConfigDir = new File(project.ext.svnConfigPath)
        if (!svnConfigDir.exists()) {
            svnConfigDir.mkdir()
        }
        return svnConfigDir
    }
    
    private def getSvnCommandName() {
        if (export) "export" else "checkout"
    }
    
    @Override
    protected String getCommandName() {
        "SVN ${getSvnCommandName()}"
    }
    
    @Override
    protected boolean DoCheckout(File destinationDir, String repoUrl, String repoRevision, String repoBranch) {
        def svnConfigDir = getSvnConfigDir()
        
        def result = TryCheckout(destinationDir, repoUrl, repoRevision, svnConfigDir)
        
        if (!result) {
            def myCredentialsExtension = project.extensions.findByName("my")
            if (myCredentialsExtension != null) {
                println "  Authentication failed. Using credentials from 'my-credentials' plugin..."
                result = TryCheckout(destinationDir, repoUrl, repoRevision, svnConfigDir, true, myCredentialsExtension.username(), myCredentialsExtension.password())
                println "  Well, that didn't work. Your \"Domain Credentials\" are probably out of date."
                println "  Have you changed your password recently? If so then please try running "
                println "  'credential-store.exe' which should be in the root of your workspace."
                throw new RuntimeException("SVN authentication failure.")
            } else {
                println "  Authentication failed. Please apply the 'my-credentials' plugin."
            }
        }
        
        result
    }
}

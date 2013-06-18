package holygradle.scm

import holygradle.custom_gradle.plugin_apis.CredentialSource
import org.gradle.api.*
import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.wc.*
import org.tmatesoft.svn.core.internal.wc.*
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler

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
    
    private boolean TryCheckout(
        File destinationFile,
        String repoUrl,
        String repoRevision,
        File svnConfigDir,
        SVNDepth depth,
        String username,
        String password
    ) {
        try {
            boolean storeCredentials = false
            if (username != null && password != null) {
                storeCredentials = true
            }
            
            SVNRevision svnRevision = SVNRevision.HEAD
            if (repoRevision != null) {
                svnRevision = SVNRevision.create(Long.parseLong(repoRevision))
            }
            
            SVNURL svnUrl = SVNURL.parseURIEncoded(repoUrl)
            DefaultSVNAuthenticationManager authManager = new DefaultSVNAuthenticationManager(
                svnConfigDir,
                storeCredentials,
                username,
                password
            )
            DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(true)
            SVNClientManager clientManager = SVNClientManager.newInstance(options, authManager)
            SVNUpdateClient updateClient = clientManager.getUpdateClient()
            updateClient.setIgnoreExternals(ignoreExternals)
            if (sourceDependency.export) {
                updateClient.doExport(svnUrl, destinationFile, svnRevision, svnRevision, "\n", false, depth)
            } else {
                updateClient.doCheckout(svnUrl, destinationFile, svnRevision, svnRevision, depth, false)
            }
            clientManager.dispose()
            
            boolean writeVersion = sourceDependency.export
            if (sourceDependency.writeVersionInfoFile != null) {
                writeVersion = sourceDependency.writeVersionInfoFile
            }
            if (writeVersion) {
                writeVersionInfoFile()
            }
            return true
        } catch (SVNAuthenticationException ignored) {
            return false
        }
    }
    
    private boolean TryCheckout(File destinationDir, String repoUrl, String repoRevision, File svnConfigDir) {
        TryCheckout(destinationDir, repoUrl, repoRevision, svnConfigDir, SVNDepth.INFINITY, null, null)
    }

    private File getSvnConfigDir() {
        // Get SVN-config path.
        File svnConfigDir = new File((String)project.ext.svnConfigPath)
        if (!svnConfigDir.exists()) {
            svnConfigDir.mkdir()
        }
        return svnConfigDir
    }
    
    private def String getSvnCommandName() {
        if (export) "export" else "checkout"
    }
    
    @Override
    protected String getCommandName() {
        "SVN ${getSvnCommandName()}"
    }
    
    @Override
    protected boolean DoCheckout(File destinationDir, String repoUrl, String repoRevision, String repoBranch) {
        File svnConfigDir = getSvnConfigDir()
        
        boolean result = TryCheckout(destinationDir, repoUrl, repoRevision, svnConfigDir)
        
        if (!result) {
            CredentialSource myCredentialsExtension = project.extensions.findByName("my") as CredentialSource
            if (myCredentialsExtension != null) {
                println "  Authentication failed. Using credentials from 'my-credentials' plugin..."
                result = TryCheckout(
                    destinationDir,
                    repoUrl,
                    repoRevision,
                    svnConfigDir,
                    SVNDepth.INFINITY,
                    myCredentialsExtension.username,
                    myCredentialsExtension.password
                )
                if (!result) {
                    deleteEmptyDir(destinationDir)
                    println "  Well, that didn't work. Your \"Domain Credentials\" are probably out of date."
                    println "  Have you changed your password recently? If so then please try running "
                    println "  'credential-store.exe' which should be in the root of your workspace."
                    throw new RuntimeException("SVN authentication failure.")
                }
            } else {
                println "  Authentication failed. Please apply the 'my-credentials' plugin."
            }
        }
        
        result
    }
}

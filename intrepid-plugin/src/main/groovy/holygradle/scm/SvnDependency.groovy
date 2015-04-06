package holygradle.scm

import holygradle.custom_gradle.plugin_apis.CredentialSource
import org.gradle.api.*
import org.gradle.process.ExecSpec
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler

class SvnDependency extends SourceDependency {
    private final Command svnCommand
    public boolean export = false
    public boolean ignoreExternals = false
    
    public SvnDependency(Project project, SourceDependencyHandler sourceDependency, Command svnCommand) {
        super(project, sourceDependency)
        this.svnCommand = svnCommand
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
        String username,
        String password
    ) {
        String result = svnCommand.execute { ExecSpec spec ->
            if (sourceDependency.export) {
                spec.args "export", "--native-eol", "LF"
            } else {
                spec.args "checkout"
            }
            if (repoRevision != null) {
                spec.args "${repoUrl}@${repoRevision}"
            } else {
                spec.args repoUrl
            }
            spec.args destinationFile.path, "--config-dir", svnConfigDir.path, "--ignore-externals"
            if (username != null) {
                spec.args "--username", username
            }
            if (username != null) {
                spec.args "--password", password
            }
        }
        boolean writeVersion = sourceDependency.export
        if (sourceDependency.writeVersionInfoFile != null) {
            writeVersion = sourceDependency.writeVersionInfoFile
        }
        if (writeVersion) {
            writeVersionInfoFile()
        }
        return true
    }
    
    private boolean TryCheckout(File destinationDir, String repoUrl, String repoRevision, File svnConfigDir) {
        TryCheckout(destinationDir, repoUrl, repoRevision, svnConfigDir, null, null)
    }

    private File getSvnConfigDir() {
        // Get SVN-config path.
        File svnConfigDir = new File((String)project.svnConfigPath)
        if (!svnConfigDir.exists()) {
            svnConfigDir.mkdir()
        }
        return svnConfigDir
    }
    
    private String getSvnCommandName() {
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

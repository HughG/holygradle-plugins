package holygradle.scm

import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class HgDependency extends SourceDependency {
    private final BuildScriptDependencies buildScriptDependencies
    private final Command hgCommand
    
    public HgDependency(
        Project project,
        SourceDependencyHandler sourceDependency,
        BuildScriptDependencies buildScriptDependencies,
        Command hgCommand
    ) {
        super(project, sourceDependency)
        this.buildScriptDependencies = buildScriptDependencies
        this.hgCommand = hgCommand
    }
    
    @Override
    public String getFetchTaskDescription() {
        "Retrieves an Hg Clone for '${sourceDependency.name}' into your workspace."
    }
    
    private void cacheCredentials(String username, String password, String repoUrl) {
        String credUrl = repoUrl.split("@")[0]
        String credentialStorePath = buildScriptDependencies.getPath("credential-store").path
        final String credentialName = "${username}@@${credUrl}@Mercurial"
        ExecResult execResult = project.exec { ExecSpec spec ->
            spec.setIgnoreExitValue true
            spec.commandLine credentialStorePath, credentialName, username, password
        }
        project.logger.debug "cacheCredentials: cache credential exit value: ${execResult.exitValue}."
        if (execResult.getExitValue() == -1073741515) {
            project.logger.info "-"*80
            project.logger.info "Failed to cache Mercurial credentials. This is probably because you don't have the " +
                    "Visual C++ 2010 Redistributable installed on your machine. Please download and " +
                    "install the x86 version before continuing. Here's the link: "
            project.logger.info "    http://www.microsoft.com/download/en/details.aspx?id=5555"
            project.logger.info "-"*80
        }
        execResult.assertNormalExitValue()
        project.logger.info "Cached credential '${credentialName}'."
    }

    private boolean TryCheckout(String repoUrl, File destinationDir, String repoBranch) {
        Collection<String> args = ["clone"]
        if (repoBranch != null) { 
            args.add("--branch")
            args.add(repoBranch)
        }
        args.add("--")
        args.add(repoUrl)
        args.add(destinationDir.path)

        project.logger.debug "tryCheckout: checking out with command line ${args}."

        try {
            hgCommand.execute { ExecSpec spec ->
                spec.workingDir = project.projectDir
                spec.args args
            }
        } catch (RuntimeException ex) {
            project.logger.error "Checkout of ${repoUrl} branch ${repoBranch} failed: ${ex.message}. " +
                                     "Full exception is recorded at debug logging level."
            project.logger.debug("Checkout of ${repoUrl} branch ${repoBranch} failed", ex)
            return false
        }
        return true
    }

    @Override
    protected String getCommandName() {
        "Hg Clone"
    }
    
    @Override
    protected boolean doCheckout(File destinationDir, String repoUrl, String repoRevision, String repoBranch) {

        boolean result = TryCheckout(repoUrl, destinationDir, repoBranch)
        
        if (!result) {
            deleteEmptyDir(destinationDir)
            CredentialSource myCredentialsExtension = project.extensions.findByName("my") as CredentialSource
            if (myCredentialsExtension != null) {
                project.logger.info "  Authentication failed. Trying credentials from 'my-credentials' plugin..."
                cacheCredentials(myCredentialsExtension.username, myCredentialsExtension.password, repoUrl)
                project.logger.info "  Cached Mercurial credentials. Trying again..."
                result = TryCheckout(repoUrl, destinationDir, repoBranch)
                if (!result) {
                    deleteEmptyDir(destinationDir)

                    if (keyringIsConfigured(destinationDir)) {
                        throw new RuntimeException(
                            "Failed to clone ${repoUrl} even after pre-caching credentials. " +
                            "The mercurial_keyring IS configured. If your password changed recently, " +
                            "try running 'credential-store.exe' which should be in the root of your workspace, " +
                            "then try again."
                        )
                    } else {
                        throw new RuntimeException(
                            "Failed to clone ${repoUrl}. The mercurial_keyring is NOT configured. " +
                            "Please configure it and try again."
                        )
                    }
                }
            } else {
                throw new RuntimeException(
                    "Failed to clone ${repoUrl}.  Cannot re-try with authentication " +
                    "because the 'my-credentials' plugin is not applied. " +
                    "Please apply the 'my-credentials' plugin and try again."
                )
            }
        }
        
        // Update to a specific revision if necessary.
        if (repoRevision != null) {
            hgCommand.execute { ExecSpec spec ->
                spec.workingDir = destinationDir
                spec.args "update", "-r", repoRevision
            }
        }
        
        result
    }

    private boolean keyringIsConfigured(File destinationDir) {
        return hgCommand.execute { ExecSpec spec ->
            spec.workingDir = destinationDir
            spec.args "config"
        }.readLines().any {
            it.startsWith("extensions.mercurial_keyring")
        }
    }
}
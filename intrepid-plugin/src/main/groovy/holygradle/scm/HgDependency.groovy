package holygradle.scm

import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project
import org.gradle.process.ExecSpec

class HgDependency extends SourceDependency {
    private final Command credentialStoreCommand
    private final Command hgCommand

    public HgDependency(
        Project project,
        SourceDependencyHandler sourceDependency,
        Command credentialStoreCommand,
        Command hgCommand
    ) {
        super(project, sourceDependency)
        this.credentialStoreCommand = credentialStoreCommand
        this.hgCommand = hgCommand
    }
    
    @Override
    public String getFetchTaskDescription() {
        "Retrieves an Hg Clone for '${sourceDependency.name}' into your workspace."
    }
    
    private void cacheCredentials(String username, String password, String repoUrl) {
        final String credUrl = repoUrl.split("@")[0]
        final String credentialName = "${username}@@${credUrl}@Mercurial"
        ScmHelper.storeCredential(project.logger, credentialStoreCommand, credentialName, username, password)
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
            boolean repoSupportsAuthentication = ScmHelper.repoSupportsAuthentication(repoUrl)
            if (!repoSupportsAuthentication) {
                throw new RuntimeException(
                    "Failed to clone ${repoUrl}.  Cannot re-try with authentication " +
                    "because repo URL is not HTTP(S): ${repoUrl}."
                )
            }
            CredentialSource myCredentialsExtension = project.extensions.findByName("my") as CredentialSource
            if (myCredentialsExtension == null) {
                throw new RuntimeException(
                    "Failed to clone ${repoUrl}.  Cannot re-try with authentication " +
                    "because the 'my-credentials' plugin is not applied. " +
                    "Please apply the 'my-credentials' plugin and try again."
                )
            }
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
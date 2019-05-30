package holygradle.scm

import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Action
import org.gradle.api.Project
import java.io.File

class HgDependency(
        project: Project,
        sourceDependency: SourceDependencyHandler,
        private val hgCommand: Command
) : SourceDependency(project, sourceDependency) {
    override val fetchTaskDescription: String
        get() = "Retrieves an Hg Clone for '${sourceDependency.name}' into your workspace."

    private fun cacheCredentials(credentialSource: CredentialSource, credentialBasis: String, repoUrl: String) {
        val credUrl = repoUrl.split("@")[0]
        val credentialName = "${credentialSource.username(credentialBasis)}@@${credUrl}@Mercurial"
        ScmHelper.storeCredential(project, credentialSource, credentialName, credentialBasis)
    }

    private fun tryCheckout(repoUrl: String, destinationDir: File, repoBranch: String?): Boolean {
        val args: MutableCollection<String> = mutableListOf("clone")
        if (repoBranch != null) { 
            args.add("--branch")
            args.add(repoBranch)
        }
        args.add("--")
        args.add(repoUrl)
        args.add(destinationDir.path)

        project.logger.debug("tryCheckout: checking out with command line ${args}.")

        try {
            hgCommand.execute(Action { spec ->
                spec.workingDir = project.projectDir
                spec.args(args)
            })
        } catch (ex: RuntimeException) {
            val baseMessage = "Checkout of ${repoUrl} branch ${repoBranch} failed"
            project.logger.error("${baseMessage}: ${ex.message}. " +
                    "Full exception is recorded at debug logging level.")
            project.logger.debug(baseMessage, ex)
            return false
        }
        return true
    }

    override val commandName: String
        get() = "Hg Clone"

    override fun doCheckout(destinationDir: File, repoUrl: String, repoRevision: String?, repoBranch: String?): Boolean {
        var result = tryCheckout(repoUrl, destinationDir, repoBranch)
        
        if (!result) {
            deleteEmptyDir(destinationDir)
            val repoSupportsAuthentication = ScmHelper.repoSupportsAuthentication(repoUrl)
            if (!repoSupportsAuthentication) {
                throw RuntimeException(
                        "Failed to clone ${repoUrl}.  Cannot re-try with authentication " +
                                "because repo URL is not HTTP(S): ${repoUrl}."
                        )
            }
            val myCredentialsExtension = project.extensions.findByName("my") as? CredentialSource
                    ?: throw RuntimeException(
                            "Failed to clone ${repoUrl}.  Cannot re-try with authentication " +
                            "because the 'my-credentials' plugin is not applied. " +
                            "Please apply the 'my-credentials' plugin and try again."
                    )
            project.logger.info("  Authentication failed. Trying credentials from 'my-credentials' plugin...")
            cacheCredentials(myCredentialsExtension, sourceDependency.credentialBasis, repoUrl)
            project.logger.info("  Cached Mercurial credentials. Trying again...")
            result = tryCheckout(repoUrl, destinationDir, repoBranch)
            if (!result) {
                deleteEmptyDir(destinationDir)

                if (keyringIsConfigured(destinationDir)) {
                    throw RuntimeException(
                        "Failed to clone ${repoUrl} even after pre-caching credentials. " +
                        "The mercurial_keyring IS configured. If your password changed recently, " +
                        "try running 'credential-store.exe' which should be in the root of your workspace, " +
                        "then try again."
                    )
                } else {
                    throw RuntimeException(
                        "Failed to clone ${repoUrl}. The mercurial_keyring is NOT configured. " +
                        "Please configure it and try again."
                    )
                }
            }
        }
        
        // Update to a specific revision if necessary.
        if (repoRevision != null) {
            hgCommand.execute(Action { spec ->
                spec.workingDir = destinationDir
                spec.args("update", "-r", repoRevision)
            })
        }
        
        return result
    }

    private fun keyringIsConfigured(destinationDir: File): Boolean {
        return hgCommand.execute(Action { spec ->
            spec.workingDir = destinationDir
            spec.args("config")
        }).lines().any {
            it.startsWith("extensions.mercurial_keyring")
        }
    }
}
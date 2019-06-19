package holygradle.scm

import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.kotlin.dsl.getValue
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Action
import org.gradle.api.Project
import java.io.File
import java.net.URL

internal class GitDependency(
        project: Project,
        sourceDependency: SourceDependencyHandler,
        private val gitCommand: Command
) : SourceDependency(project, sourceDependency) {
    override val fetchTaskDescription: String
        get() = "Retrieves a Git Clone for '${sourceDependency.name}' into your workspace."

    private fun cacheCredentials(credentialSource: CredentialSource, credentialBasis: String, repoUrl: String) {
        val parsedUrl = URL(repoUrl)
        val repoScheme = parsedUrl.protocol
        val repoHost = parsedUrl.host
        val credentialName = "git:${repoScheme}://${repoHost}"
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
            gitCommand.execute(Action { spec ->
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
        get() = "Git Clone"

    override fun doCheckout(destinationDir: File, repoUrl: String, repoRevision: String?, repoBranch: String?): Boolean {
        // Git on Windows has at least three different credential managers which integrate with the Windows Credential
        // Manager: wincred, winstore (https://gitcredentialstore.codeplex.com/), and manager
        // (https://github.com/Microsoft/Git-Credential-Manager-for-Windows).  Of these, both winstore and manager will
        // by default pop up a dialog to prompt for credentials.  In all versions of winstore and some versions of
        // manager this dialog can't be disabled, which would be bad on an auto-build machine.  There's no way to just
        // ask whether credentials are available without prompting for them so, to avoid popping up a dialog, which
        // never times out, on autobuild machines, and thereby hanging a build, we just pre-cache credendtials for the
        // URL, even though they might not be needed.  We could handle wincred specially, only caching credentials if it
        // fails, but I'd rather keep things consistent.
        //
        // I tried out checking in advance whether authentication is needed by doing an initial direct HTTP GET for
        // "${sourceUrl}/info/refs?service=git-receive-pack", as described in
        // https://git-scm.com/book/en/v2/Git-Internals-Transfer-Protocols and https://gist.github.com/schacon/6092633.
        // If a repo requires authentication the response should be 401, otherwise 200.  This seemed to work fine for
        // GitLab and GutHub, but not for BitBucket: the latter would sometimes return 401 for a public repo, and then
        // later requests might start to return 200 for no apparent reason.  Also, the Git HTTP protocol has already
        // changed once (from "dumb" to "smart") and so might change again, and the aforementioned servers had varied
        // responses to requests using the "dumb" protocol.  Therefore I decided to keep it simple and consistent, and
        // just always pre-cache credentials.

        val myCredentialsExtension = project.extensions.findByName("my") as CredentialSource?
        val credentialHelperIsConfigured = getCredentialHelperIsConfigured()
        val repoSupportsAuthentication = ScmHelper.repoSupportsAuthentication(repoUrl)
        if (repoSupportsAuthentication) {
            if (myCredentialsExtension != null) {
                if (credentialHelperIsConfigured) {
                    project.logger.info("  Pre-caching credentials for Git from 'my-credentials' plugin...")
                    cacheCredentials(myCredentialsExtension, sourceDependency.credentialBasis, repoUrl)
                } else {
                    project.logger.info("  Not pre-caching credentials because the Git credential.helper is not configured.")
                }
            } else {
                project.logger.info("  Not pre-caching credentials because the 'my-credentials' plugin is not applied.")
            }
        } else {
            project.logger.info("  Not pre-caching credentials because the repo URL is not HTTP(S): ${repoUrl}.")
        }

        val result = tryCheckout(repoUrl, destinationDir, repoBranch)

        if (!result) {
            deleteEmptyDir(destinationDir)
            if (!repoSupportsAuthentication) {
                throw RuntimeException(
                        "Failed to clone ${repoUrl}.  Could not try with authentication " +
                                "because repo URL is not HTTP(S): ${repoUrl}."
                        )
            }
            if (myCredentialsExtension == null) {
                throw RuntimeException(
                        "Failed to clone ${repoUrl}.  Could not try with authentication " +
                                "because the 'my-credentials' plugin is not applied. " +
                                "Please apply the 'my-credentials' plugin and try again."
                        )
            }
            if (credentialHelperIsConfigured) {
                throw RuntimeException(
                        "Failed to clone ${repoUrl} even after caching credentials. " +
                                "The Git credential.helper IS configured. If your password changed recently, " +
                                "try running 'credential-store.exe' which should be in the root of your workspace, then try again."
                        )
            }
            throw RuntimeException(
                    "Failed to clone ${repoUrl}. Cannot try with authentication " +
                            "because the Git credential.helper is NOT configured. " +
                            "Please configure it and try again."
                    )
        }


        // Update to a specific revision if necessary.
        if (repoRevision != null) {
            gitCommand.execute(Action { spec ->
                spec.workingDir = destinationDir
                spec.args("checkout", repoRevision)
            })
        }

        return result
    }

    private fun getCredentialHelperIsConfigured(): Boolean {
        return ScmHelper.getGitConfigValue(gitCommand, project.projectDir, "credential.helper")
                .lineSequence().any { it.isNotBlank() }
    }
}
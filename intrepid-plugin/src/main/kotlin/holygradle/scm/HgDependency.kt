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
        private val buildScriptDependencies: BuildScriptDependencies,
        private val hgCommand: Command
) : SourceDependency(project, sourceDependency) {
    override val fetchTaskDescription: String
        get() = "Retrieves an Hg Clone for '${sourceDependency.name}' into your workspace."

    private fun cacheCredentials(username: String, password: String, repoUrl: String) {
        val credUrl = repoUrl.split("@")[0]
        val credentialStorePath = buildScriptDependencies.getPath("credential-store")?.path
                ?: throw RuntimeException("Failed to find credential-store.exe")
        // println "${credentialStorePath} ${credUrl} ${username} <password>"
        val execResult = project.exec { spec ->
            spec.isIgnoreExitValue = true
            spec.commandLine(credentialStorePath, "${username}@@${credUrl}@Mercurial", username, password)
        }
        if (execResult.exitValue == -1073741515) {
            val separator = "-".repeat(80)
            println(separator)
            println("Failed to cache Mercurial credentials. This is probably because you don't have the " +
                    "Visual C++ 2010 Redistributable installed on your machine. Please download and " +
                    "install the x86 version before continuing. Here's the link: ")
            println("    http://www.microsoft.com/download/en/details.aspx?id=5555")
            println(separator)
        }
        execResult.assertNormalExitValue()
    }

    private fun TryCheckout(repoUrl: String, destinationDir: File, repoBranch: String?): Boolean {
        val args: MutableCollection<String> = mutableListOf("clone")
        if (repoBranch != null) { 
            args.add("--branch")
            args.add(repoBranch)
        }
        args.add("--")
        args.add(repoUrl)
        args.add(destinationDir.path)

        try {
            hgCommand.execute(Action { spec ->
                spec.workingDir = project.projectDir
                spec.args(args)
            })
        } catch (ex: RuntimeException) {
            println(ex.message)
            return false
        }
        return true
    }

    override val commandName: String
        get() = "Hg Clone"

    override fun DoCheckout(destinationDir: File, repoUrl: String, repoRevision: String?, repoBranch: String?): Boolean {
        var result = TryCheckout(repoUrl, destinationDir, repoBranch)
        
        if (!result) {
            deleteEmptyDir(destinationDir)
            val myCredentialsExtension = project.extensions.findByName("my") as? CredentialSource
            if (myCredentialsExtension != null) {
                println("  Authentication failed. Trying credentials from 'my-credentials' plugin...")
                cacheCredentials(myCredentialsExtension.username, myCredentialsExtension.password, repoUrl)
                println("  Cached credentials. Trying again...")
                result = TryCheckout(repoUrl, destinationDir, repoBranch)
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
            } else {
                throw RuntimeException(
                    "Failed to clone ${repoUrl}.  Cannot re-try with authentication " +
                    "because the 'my-credentials' plugin is not applied. " +
                    "Please apply the 'my-credentials' plugin and try again."
                )
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
package holygradle.scm

import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class GitDependency extends SourceDependency {
    private final BuildScriptDependencies buildScriptDependencies
    private final Command gitCommand

    public GitDependency(
        Project project,
        SourceDependencyHandler sourceDependency,
        BuildScriptDependencies buildScriptDependencies,
        Command gitCommand
    ) {
        super(project, sourceDependency)
        this.buildScriptDependencies = buildScriptDependencies
        this.gitCommand = gitCommand
    }

    @Override
    public String getFetchTaskDescription() {
        "Retrieves a Git Clone for '${sourceDependency.name}' into your workspace."
    }

    private void cacheCredentials(String username, String password, String repoUrl) {
        if (!credentialHelperIsConfigured()) {
            // Enable Windows Credential Manager support
            ExecResult execResult = project.exec { ExecSpec spec ->
                spec.setIgnoreExitValue true
                spec.commandLine "git config credential.helper wincred"
            }
            execResult.assertNormalExitValue()
        }

        URL parsedUrl = new URL(repoUrl)
        String repoScheme = parsedUrl.getProtocol()
        String repoHost = parsedUrl.getHost()
        String credentialStorePath = buildScriptDependencies.getPath("credential-store").path
        // println "${credentialStorePath} ${credUrl} ${username} <password>"
        ExecResult execResult = project.exec { ExecSpec spec ->
            spec.setIgnoreExitValue true
            spec.commandLine credentialStorePath, "git://${repoScheme}://${username}@${repoHost}", username, password
        }
        if (execResult.getExitValue() == -1073741515) {
            println "-"*80
            println "Failed to cache Git credentials. This is probably because you don't have the " +
                    "Visual C++ 2010 Redistributable installed on your machine. Please download and " +
                    "install the x86 version before continuing. Here's the link: "
            println "    http://www.microsoft.com/download/en/details.aspx?id=5555"
            println "-"*80
        }
        execResult.assertNormalExitValue()
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

        try {
            gitCommand.execute { ExecSpec spec ->
                spec.workingDir = project.projectDir
                spec.args args
            }
        } catch (RuntimeException ex) {
            println(ex.message)
            return false
        }
        return true
    }

    @Override
    protected String getCommandName() {
        "Git Clone"
    }

    @Override
    protected boolean DoCheckout(File destinationDir, String repoUrl, String repoRevision, String repoBranch) {

        boolean result = TryCheckout(repoUrl, destinationDir, repoBranch)

        if (!result) {
            deleteEmptyDir(destinationDir)
            CredentialSource myCredentialsExtension = project.extensions.findByName("my") as CredentialSource
            if (myCredentialsExtension != null) {
                println "  Authentication failed. Trying credentials from 'my-credentials' plugin..."
                cacheCredentials(myCredentialsExtension.username, myCredentialsExtension.password, repoUrl)
                println "  Cached Git credentials. Trying again..."
                result = TryCheckout(repoUrl, destinationDir, repoBranch)
                if (!result) {
                    deleteEmptyDir(destinationDir)

                    if (credentialHelperIsConfigured()) {
                        throw new RuntimeException(
                            "Failed to clone ${repoUrl} even after pre-caching credentials. " +
                            "The credential.helper IS configured. If your password changed recently, " +
                            "try running 'credential-store.exe' which should be in the root of your workspace, " +
                            "then try again."
                        )
                    } else {
                        throw new RuntimeException(
                            "Failed to clone ${repoUrl}. The credential.helper is NOT configured. " +
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
            gitCommand.execute { ExecSpec spec ->
                spec.workingDir = destinationDir
                spec.args "checkout", repoRevision
            }
        }

        result
    }

    private boolean credentialHelperIsConfigured() {
        return gitCommand.execute { ExecSpec spec ->
            spec.args "config --get credential.helper"
        }.readLines().any {
            it.startsWith("wincred")
        }
    }
}
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
        URL parsedUrl = new URL(repoUrl)
        String repoScheme = parsedUrl.getProtocol()
        String repoHost = parsedUrl.getHost()
        String credentialStorePath = buildScriptDependencies.getPath("credential-store").path
        // println "${credentialStorePath} ${credUrl} ${username} <password>"
        def credentialName = "git://${repoScheme}://${username}@${repoHost}"
        ExecResult execResult = project.exec { ExecSpec spec ->
            spec.setIgnoreExitValue true
            spec.commandLine credentialStorePath, credentialName, username, password
        }
        println "cacheCredentials: cache credential exit value: ${execResult.exitValue}."
        if (execResult.getExitValue() == -1073741515) {
            println "-"*80
            println "Failed to cache Git credentials. This is probably because you don't have the " +
                    "Visual C++ 2010 Redistributable installed on your machine. Please download and " +
                    "install the x86 version before continuing. Here's the link: "
            println "    http://www.microsoft.com/download/en/details.aspx?id=5555"
            println "-"*80
        }
        execResult.assertNormalExitValue()
        println "Cached credential '${credentialName}'."
    }

    private boolean tryCheckout(String repoUrl, File destinationDir, String repoBranch) {
        Collection<String> args = ["clone"]
        if (repoBranch != null) {
            args.add("--branch")
            args.add(repoBranch)
        }
        args.add("--")
        args.add(repoUrl)
        args.add(destinationDir.path)

        println "tryCheckout: checking out with command line ${args}."

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
    protected boolean doCheckout(File destinationDir, String repoUrl, String repoRevision, String repoBranch) {
        // Git on Windows has at least three different credential managers which integrate with the Windows Credential
        // Manager: wincred, winstore (https://gitcredentialstore.codeplex.com/), and manager
        // (https://github.com/Microsoft/Git-Credential-Manager-for-Windows).  Of these, both winstore and manager will
        // by default pop up a dialog to prompt for credentials.  In all versions of winstore and some versions of
        // manager this dialog can't be disabled, which would be bad on an auto-build machine.
        //
        // Therefore, unless wincred is configured, we always pre-emptively cache credentials just in case.
        CredentialSource myCredentialsExtension = project.extensions.findByName("my") as CredentialSource
        String credentialHelper = getConfiguredCredentialHelper()
        def wincredIsConfigured = (credentialHelper == "wincred")
        if (myCredentialsExtension != null) {
            if (!wincredIsConfigured) {
                println "  Pre-caching credentials for Git helper '${credentialHelper}' from 'my-credentials' plugin..."
                cacheCredentials(myCredentialsExtension.username, myCredentialsExtension.password, repoUrl)
            }
        } else {
            println "  Not pre-caching credentials because the 'my-credentials' plugin is not applied."
        }

        boolean result = tryCheckout(repoUrl, destinationDir, repoBranch)

        if (!result) {
            deleteEmptyDir(destinationDir)
            if (myCredentialsExtension != null) {
                throw new RuntimeException(
                    "Failed to clone ${repoUrl}.  Cannot re-try with authentication " +
                    "because the 'my-credentials' plugin is not applied. " +
                    "Please apply the 'my-credentials' plugin and try again."
                )
            }
            if (credentialHelper == null) {
                throw new RuntimeException(
                    "Failed to clone ${repoUrl}. Cannot try with authentication " +
                    "because the Git credential.helper is NOT configured. " +
                    "Please configure it and try again."
                )
            }
            if (!wincredIsConfigured) {
                throw new RuntimeException(
                    "Failed to clone ${repoUrl}. Already pre-cached credentials for Git helper '${credentialHelper}'. " +
                    "There must be some other problem."
                )
            }

            println "  Authentication failed. " +
                "Caching credentials for helper '${credentialHelper}' from 'my-credentials' plugin..."
            cacheCredentials(myCredentialsExtension.username, myCredentialsExtension.password, repoUrl)
            println "  Cached Git credentials. Trying again..."
            result = tryCheckout(repoUrl, destinationDir, repoBranch)
            if (!result) {
                deleteEmptyDir(destinationDir)

                if (credentialHelper != null) {
                    throw new RuntimeException(
                        "Failed to clone ${repoUrl} even after caching credentials. " +
                        "The Git credential.helper IS configured to '${credentialHelper}'. " +
                        "If your password changed recently, " +
                        "try running 'credential-store.exe' which should be in the root of your workspace, " +
                        "then try again."
                    )
                }
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

    private String getConfiguredCredentialHelper() {
        def trimmedLines = GitHelper.getConfigValue(gitCommand, project.projectDir, "credential.helper")
            .readLines().collect { it.trim() }
        return trimmedLines.find { !it.isEmpty() }
    }
}
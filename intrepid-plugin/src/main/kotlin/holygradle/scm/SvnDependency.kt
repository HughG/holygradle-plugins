package holygradle.scm

import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.io.FileHelper
import holygradle.kotlin.dsl.getValue
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.process.ExecSpec
import java.io.File

class SvnDependency(
        project: Project,
        sourceDependency: SourceDependencyHandler,
        private val svnCommand: Command
) : SourceDependency(project, sourceDependency) {
    var export = false

    override val fetchTaskDescription: String =
            "Retrieves an SVN checkout for '${sourceDependency.name}' into your workspace."

    private fun tryCheckout(
            destinationFile: File,
            repoUrl: String,
            repoRevision: String?,
            svnConfigDir: File,
            username: String? = null,
            password: String? = null
    ): Boolean {
        svnCommand.execute(Action { spec: ExecSpec ->
            if (sourceDependency.export) {
                spec.args("export", "--native-eol", "LF")
            } else {
                spec.args("checkout")
            }
            if (repoRevision != null) {
                spec.args("${repoUrl}@${repoRevision}")
            } else {
                spec.args(repoUrl)
            }
            spec.args(destinationFile.path, "--config-dir", svnConfigDir.path, "--ignore-externals")
            if (username != null) {
                spec.args("--username", username)
            }
            if (username != null) {
                spec.args("--password", password)
            }
        })
        val writeVersion = sourceDependency.writeVersionInfoFile ?: sourceDependency.export
        if (writeVersion) {
            writeVersionInfoFile()
        }
        return true
    }

    private val svnConfigDir: File get () {
        // Get SVN-config path.
        val ext: ExtraPropertiesExtension by project.extensions
        val svnConfigPath: String by ext
        val svnConfigDir = File(svnConfigPath)
        FileHelper.ensureMkdirs(svnConfigDir, "(subversion configuration dir)")
        return svnConfigDir
    }
    
    private val svnCommandName: String
        get() = if (export) "export" else "checkout"

    override val commandName: String
        get() = "SVN ${svnCommandName}"

    override fun doCheckout(destinationDir: File, repoUrl: String, repoRevision: String?, repoBranch: String?): Boolean {
        val svnConfigDir = svnConfigDir

        var result = tryCheckout(destinationDir, repoUrl, repoRevision, svnConfigDir)
        
        if (!result) {
            val myCredentialsExtension = project.extensions.findByName("my") as? CredentialSource
                    ?: throw RuntimeException(
                    "Failed to ${svnCommandName} ${repoUrl}.  Could not try with authentication " +
                            "because the 'my-credentials' plugin is not applied. " +
                            "Please apply the 'my-credentials' plugin and try again."
            )
            project.logger.info("  Authentication failed. Using credentials from 'my-credentials' plugin...")
            val credentialBasis = sourceDependency.credentialBasis
            result = tryCheckout(
                destinationDir,
                repoUrl,
                repoRevision,
                svnConfigDir,
                myCredentialsExtension.username(credentialBasis),
                myCredentialsExtension.username(credentialBasis)
            )
            if (!result) {
                deleteEmptyDir(destinationDir)
                throw RuntimeException(
                        "Failed to clone ${repoUrl} even after using credentials from 'my-credentials' plugin. " +
                                "If your password changed recently, " +
                                "try running 'credential-store.exe' which should be in the root of your workspace, then try again."
                        )
            }
        }
        
        return result
    }
}

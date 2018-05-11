package holygradle.scm

import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.io.FileHelper
import holygradle.source_dependencies.SourceDependency
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.process.ExecSpec
import holygradle.kotlin.dsl.getValue
import java.io.File

class SvnDependency(
        project: Project,
        sourceDependency: SourceDependencyHandler,
        private val svnCommand: Command
) : SourceDependency(project, sourceDependency) {
    var export = false

    override val fetchTaskDescription: String =
            "Retrieves an SVN Checkout for '${sourceDependency.name}' into your workspace."

    private fun TryCheckout(
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

    override fun DoCheckout(destinationDir: File, repoUrl: String, repoRevision: String?, repoBranch: String?): Boolean {
        val svnConfigDir = svnConfigDir

        var result = TryCheckout(destinationDir, repoUrl, repoRevision, svnConfigDir)
        
        if (!result) {
            val myCredentialsExtension = project.extensions.findByName("my") as? CredentialSource
            if (myCredentialsExtension != null) {
                println("  Authentication failed. Using credentials from 'my-credentials' plugin...")
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
                    project.logger.error("  ${commandName} failed. Your \"Domain Credentials\" are probably out of date.")
                    project.logger.error("  Have you changed your password recently? If so then please try running ")
                    project.logger.error("  'credential-store.exe' which should be in the root of your workspace.")
                    throw RuntimeException("SVN authentication failure.")
                }
            } else {
                project.logger.error("  Authentication failed. Please apply the 'my-credentials' plugin.")
            }
        }
        
        return result
    }
}

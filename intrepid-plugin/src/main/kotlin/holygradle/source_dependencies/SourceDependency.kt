package holygradle.source_dependencies

import holygradle.io.FileHelper
import org.gradle.api.*
import holygradle.Helper
import java.io.File

abstract class SourceDependency(
        val project: Project,
        val sourceDependency: SourceDependencyHandler
) {
    companion object {
        @JvmStatic
        protected fun deleteEmptyDir(dir: File) {
            if (dir.exists()) {
                if (dir.list().isEmpty()) {
                    FileHelper.ensureDeleteDirRecursive(dir)
                }
            }
        }
    }

    val url get() = sourceDependency.url
    
    val destinationDir: File get() = sourceDependency.destinationDir

    fun writeVersionInfoFile() {
        val versionInfoFile = File(sourceDependency.destinationDir, "version_info.txt")
        versionInfoFile.writeText(sourceDependency.url)
    }

    protected abstract val commandName: String

    protected abstract fun DoCheckout(
            destinationDir: File,
            repoUrl: String,
            repoRevision: String?,
            repoBranch: String?
    ): Boolean

    fun Checkout() {
        val urlSplit = url.split("@")
        var urlOnly = url
        var revision: String? = null // meaning trunk
        if (urlSplit.size == 2) {
            urlOnly = urlSplit[0]
            revision = urlSplit[1] 
        } else if (urlSplit.size > 2) {
            throw RuntimeException("Error in url '${url}'. At most one @ should be specified to indicate a particular revision.")
        }
        
        val destinationDir = sourceDependency.destinationDir
        val relativePath = Helper.relativizePath(destinationDir, project.rootProject.projectDir)
        val branchName = sourceDependency.branch
        val branchText = branchName ?: "default"
        val revText = if (revision == null) "head" else "rev: $revision"
        println("${commandName} from '${urlOnly}' ($branchText, $revText) to '<workspace>/${relativePath}'...")
        
        val result = DoCheckout(destinationDir, urlOnly, revision, branchName)
        println("  ${commandName} ${if (result) "succeeded" else "failed"}.")
    }

    abstract val fetchTaskDescription: String
}
package holygradle.source_dependencies

import holygradle.io.FileHelper
import org.gradle.api.*
import holygradle.Helper

abstract class SourceDependency {
    public final SourceDependencyHandler sourceDependency
    public final Project project

    public SourceDependency(Project project, SourceDependencyHandler sourceDependency) {
        this.sourceDependency = sourceDependency
        this.project = project
    }

    protected static void deleteEmptyDir(File dir) {
        if (dir.exists()) {
            if (dir.list().length == 0) {
                FileHelper.ensureDeleteDirRecursive(dir)
            }
        }
    }

    public String getUrl() {
        sourceDependency.url
    }
    
    public File getDestinationDir() {
        sourceDependency.getDestinationDir()
    }
    
    public void writeVersionInfoFile() {
        File versionInfoFile = new File(sourceDependency.getDestinationDir(), "version_info.txt")
        versionInfoFile.write(sourceDependency.url)
    }

    protected abstract String getCommandName()
    
    protected abstract boolean DoCheckout(File destinationDir, String repoUrl, String repoRevision, String repoBranch)
    
    public void Checkout() {
        String[] urlSplit = url.split("@")
        String urlOnly = url
        String revision = null // meaning trunk
        if (urlSplit.size() == 2) {
            urlOnly = urlSplit[0]
            revision = urlSplit[1] 
        } else if (urlSplit.size() > 2) {
            throw new RuntimeException("Error in url '${url}'. At most one @ should be specified to indicate a particular revision.")
        }
        
        File destinationDir = sourceDependency.getDestinationDir()
        String relativePath = Helper.relativizePath(destinationDir, project.rootProject.projectDir)
        String branchName = sourceDependency.branch
        String commandName = getCommandName()
        String branchText = (branchName == null) ? "default" : branchName
        String revText = (revision == null) ? "head" : "rev: $revision"
        println "${commandName} from '${urlOnly}' ($branchText, $revText) to '<workspace>/${relativePath}'..."
        
        boolean result = DoCheckout(destinationDir, urlOnly, revision, branchName)
        
        if (result) {
            println "  ${commandName} succeeded."
        } else {
            println "  ${commandName} failed."
        }
    }

    public abstract String getFetchTaskDescription()
}
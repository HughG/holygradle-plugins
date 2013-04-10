package holygradle.source_dependencies

import org.gradle.api.*
import holygradle.Helper

class SourceDependency {
    SourceDependencyHandler sourceDependency
    Project project
    File destinationDir
    
    public SourceDependency(Project project, SourceDependencyHandler sourceDependency) {
        this.sourceDependency = sourceDependency
        this.project = project
    }
    
    public String getUrl() {
        sourceDependency.url
    }
    
    public File getDestinationDir() {
        sourceDependency.getDestinationDir()
    }
    
    public void writeVersionInfoFile() {
        def versionInfoFile = new File(sourceDependency.getDestinationDir(), "version_info.txt")
        versionInfoFile.write(sourceDependency.url)
    }

    protected String getCommandName() {
    }
    
    protected boolean DoCheckout(File destinationDir, String repoUrl, String repoRevision, String repoBranch) {
    }
    
    public void Checkout() {
        def urlSplit = url.split("@")
        def urlOnly = url
        def revision = null // meaning trunk
        if (urlSplit.size() == 2) {
            urlOnly = urlSplit[0]
            revision = urlSplit[1] 
        } else if (urlSplit.size() > 2) {
            throw new RuntimeException("Error in url '${url}'. At most one @ should be specified to indicate a particular revision.")
        }
        
        def destinationDir = sourceDependency.getDestinationDir()
        def relativePath = Helper.relativizePath(destinationDir, project.rootProject.projectDir)
        def branchName = sourceDependency.branch
        def commandName = getCommandName()
        def branchText = (branchName == null) ? "default" : branchName
        def revText = (revision == null) ? "head" : "rev: $revision"
        println "${commandName} from '${urlOnly}' ($branchText, $revText) to '<workspace>/${relativePath}'..."
        
        boolean result = DoCheckout(destinationDir, urlOnly, revision, branchName)
        
        if (result) {
            println "  ${commandName} succeeded."
        } else {
            println "  ${commandName} failed."
        }
    }
    
    public String getFetchTaskDescription() {
        "description"
    }
}
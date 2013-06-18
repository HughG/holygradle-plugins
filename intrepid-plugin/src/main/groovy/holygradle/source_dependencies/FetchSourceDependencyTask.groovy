package holygradle.source_dependencies

import org.gradle.api.*
import org.gradle.api.tasks.*

class FetchSourceDependencyTask extends DefaultTask {
    private SourceDependency sourceDependency
    private String url
    private File destinationDir

    @Input
    String getUrl() {
        return url
    }

    @OutputDirectory
    File getDestinationDir() {
        return destinationDir
    }

    SourceDependency getSourceDependency() {
        return sourceDependency
    }

    
    public void initialize(SourceDependency sourceDependency) {
        this.sourceDependency = sourceDependency
        url = sourceDependency.getUrl()
        destinationDir = sourceDependency.getDestinationDir()
        onlyIf {
            !destinationDir.exists()
        }
    }
        
    @TaskAction
    public void Checkout() {
        sourceDependency.Checkout()
    }
    
    public String getSourceDirName() {
        sourceDependency.sourceDependency.name
    }
}
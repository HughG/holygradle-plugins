package holygradle

import org.gradle.api.*
import org.gradle.api.tasks.*

class FetchSourceDependencyTask extends DefaultTask {
    public SourceDependency sourceDependency
    @Input url
    @OutputDirectory destinationDir
    
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
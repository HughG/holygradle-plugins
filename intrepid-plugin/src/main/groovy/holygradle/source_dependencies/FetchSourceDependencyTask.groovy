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
        // If the destination folder already exists, don't try to fetch the source again.  If it doesn't exist (for
        // example, because the user deleted it, or the previous checkout failed), always try to check out.  (Without
        // the "upToDateWhen" call, a failed fetch seems to cause this task to be treated as UP-TO-DATE in future runs,
        // even though the output folder doesn't exist.  That happens even if I set the output folder as a task output
        // dir.)
        onlyIf {
            def destinationDirExists = destinationDir.exists()
            if (destinationDirExists) {
                println "Not fetching '${sourceDependency.url}' because target folder '${destinationDir}' exists"
            }
            return !destinationDirExists
        }
        outputs.upToDateWhen { false }
    }
        
    @TaskAction
    public void checkout() {
        sourceDependency.checkout()
    }
}
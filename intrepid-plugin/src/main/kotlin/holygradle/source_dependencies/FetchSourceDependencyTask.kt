package holygradle.source_dependencies

import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.File

open class FetchSourceDependencyTask : DefaultTask() {
    lateinit var sourceDependency: SourceDependency
        private set

    @get:Input
    lateinit var url: String
        private set

    @get:OutputDirectory
    lateinit var destinationDir: File
        private set


    fun initialize(sourceDependency: SourceDependency) {
        this.sourceDependency = sourceDependency
        url = sourceDependency.url
        destinationDir = sourceDependency.destinationDir
        // If the destination folder already exists, don't try to fetch the source again.  If it doesn't exist (for
        // example, because the user deleted it, or the previous checkout failed), always try to check out.  (Without
        // the "upToDateWhen" call, a failed fetch seems to cause this task to be treated as UP-TO-DATE in future runs,
        // even though the output folder doesn't exist.  That happens even if I set the output folder as a task output
        // dir.)
        onlyIf {
            val destinationDirExists = destinationDir.exists()
            if (destinationDirExists) {
                project.logger.info(
                        "Not fetching '${sourceDependency.url}' because target folder '${destinationDir}' exists")
            }
            !destinationDirExists
        }
    }

    @TaskAction
    fun checkout() {
        sourceDependency.checkout()
    }
}
package holygradle.source_dependencies

import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.File

class FetchSourceDependencyTask : DefaultTask() {
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
        onlyIf {
            !destinationDir.exists()
        }
    }
        
    @TaskAction
    fun Checkout() {
        sourceDependency.Checkout()
    }
    
    val sourceDirName: String get() = sourceDependency.sourceDependency.name
}
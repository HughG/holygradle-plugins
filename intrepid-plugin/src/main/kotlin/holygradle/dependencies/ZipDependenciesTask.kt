package holygradle.dependencies

import org.gradle.api.tasks.bundling.Zip
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.jvm.jvmName

open class ZipDependenciesTask : Zip() {
    fun initialize() {
        if (project != project.rootProject) {
            throw RuntimeException("${this::class.jvmName} can only be used in the root project.")
        }

        destinationDir = project.rootProject.projectDir

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        val date = dateFormat.format(Date())
        baseName = "${CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME}_${date}_UTC"

        doFirst {
            logger.info("Copying dependencies to ${archivePath}")
        }

        into(CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME) { localArtifactsSpec ->
            CollectDependenciesHelper(this).configure(localArtifactsSpec)
        }
    }
}
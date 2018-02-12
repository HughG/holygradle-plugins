package holygradle.dependencies

import org.gradle.api.tasks.Copy
import java.io.File
import kotlin.reflect.jvm.jvmName

class CollectDependenciesTask : Copy() {
    fun initialize() {
        if (project != project.rootProject) {
            throw RuntimeException("${this::class.jvmName} can only be used in the root project.")
        }

        destinationDir = File(project.rootProject.projectDir, CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME)
        doFirst {
            logger.info("Copying dependencies to ${destinationDir}")
            if (destinationDir.exists()) {
                throw RuntimeException(
                    "Cannot run CollectDependenciesTask when ${destinationDir} already exists, " +
                    "because it would overwrite files for the running version of the holygradle plugins."
                )
            }
        }

        CollectDependenciesHelper(this).configure(this)
    }
}
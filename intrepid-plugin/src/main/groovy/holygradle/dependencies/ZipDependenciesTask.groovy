package holygradle.dependencies

import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.bundling.Zip

import java.text.SimpleDateFormat

class ZipDependenciesTask extends Zip {
    public void initialize() {
        if (project != project.rootProject) {
            throw new RuntimeException("${this.class.name} can only be used in the root project.")
        }

        destinationDir = project.rootProject.projectDir

        def dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = dateFormat.format(new Date())
        baseName = "${CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME}_${date}_UTC"

        doFirst {
            logger.info("Copying dependencies to ${archivePath}")
        }

        into(CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME) { CopySpec localArtifactsSpec ->
            new CollectDependenciesHelper(this).configure(localArtifactsSpec)
        }
    }
}
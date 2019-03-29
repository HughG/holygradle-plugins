package holygradle.dependencies

import org.gradle.api.tasks.Copy

class CollectDependenciesTask extends Copy {
    public void initialize() {
        if (project != project.rootProject) {
            throw new RuntimeException("${this.class.name} can only be used in the root project.")
        }

        destinationDir = new File(project.rootProject.projectDir, CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME)
        // This has to be an "onlyIf" block, rather than "doFirst", because more recent versions of Gradle create the
        // output folder for a task before the doFirst block executes.
        onlyIf {
            logger.info("Copying dependencies to ${destinationDir}")
            if (destinationDir.exists()) {
                throw new RuntimeException(
                    "Cannot run CollectDependenciesTask when ${destinationDir} already exists, " +
                    "because it would overwrite files for the running version of the holygradle plugins."
                )
            }
            return true
        }

        new CollectDependenciesHelper(this).configure(this)
    }
}
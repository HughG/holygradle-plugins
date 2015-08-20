package holygradle.dependencies

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree

import static org.junit.Assert.assertEquals

class ZipDependenciesIntegrationTest extends CollectDependenciesIntegrationTestBase {

    private static FileTree getCollectedDependenciesFiles(Project project) {
        return project.fileTree(project.projectDir) { ConfigurableFileTree fileTree ->
            fileTree.include "${CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME}_*.zip"
        }
    }

    protected void ensureCollectedDependenciesDeleted(Project project) {
        project.delete(getCollectedDependenciesFiles(project))
    }

    protected void checkCollectedDependenciesExist(Project project) {
        assertEquals(
            "Exactly one collected dependencies ZIP exists",
            1,
            getCollectedDependenciesFiles(project).files.size()
        )
    }

    protected FileTree getCollectedArtifactsFileTree(Project project) {
        return project.zipTree(getCollectedDependenciesFiles(project).singleFile)
    }

    protected String getCollectTaskName() {
        return "zipDependencies"
    }

    @Override
    protected String getCollectedFilesPathPrefix() {
        return CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME + "/"
    }
}

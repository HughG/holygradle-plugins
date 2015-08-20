package holygradle.dependencies

import org.gradle.api.Project
import org.gradle.api.file.FileTree

import static org.junit.Assert.assertFalse

class CollectDependenciesIntegrationTest extends CollectDependenciesIntegrationTestBase {
    private static FileTree getCollectedDependenciesFiles(Project project) {
        return project.fileTree(new File(project.projectDir, CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME))
    }

    protected void ensureCollectedDependenciesDeleted(Project project) {
        project.delete(getCollectedDependenciesFiles(project))
    }

    protected void checkCollectedDependenciesExist(Project project) {
        assertFalse(
            "Some collected dependencies files exist",
            getCollectedDependenciesFiles(project).empty
        )
    }

    protected FileTree getCollectedArtifactsFileTree(Project project) {
        return getCollectedDependenciesFiles(project)
    }

    protected String getCollectTaskName() {
        return "collectDependencies"
    }

    @Override
    protected String getCollectedFilesPathPrefix() {
        return ""
    }
}

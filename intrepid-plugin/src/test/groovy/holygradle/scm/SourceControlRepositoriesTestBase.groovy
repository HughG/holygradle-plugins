package holygradle.scm

import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

abstract class SourceControlRepositoriesTestBase extends AbstractHolyGradleTest {
    protected static final String EXAMPLE_FILE = "ahoy.txt"

    @Test
    public void testBasicUsage() {
        File repoDir = getRepoDir("BU")
        FileHelper.ensureDeleteDirRecursive(repoDir)
        File projectDir = repoDir
        FileHelper.ensureMkdirs(projectDir)
        prepareRepoDir(repoDir)
        Project project = prepareProjectDir(projectDir)

        checkInitialState(project, repoDir)
        addFile(project)
        checkStateWithAddedFile(project)
    }

    @Test
    public void testProjectInSubDir() {
        File repoDir = getRepoDir("PISD")
        FileHelper.ensureDeleteDirRecursive(repoDir)
        File projectDir = new File(repoDir, "sub")
        FileHelper.ensureMkdirs(projectDir)
        prepareRepoDir(repoDir)
        Project project = prepareProjectDir(projectDir)

        checkInitialState(project, repoDir)
        addFile(project)
        checkStateWithAddedFile(project)
    }

    @Test
    public void testProjectInIgnoredSubDir() {
        File repoDir = getRepoDir("PIISD")
        FileHelper.ensureDeleteDirRecursive(repoDir)
        File projectDir = new File(repoDir, "ignored")
        FileHelper.ensureMkdirs(projectDir)
        prepareRepoDir(repoDir)
        Project project = prepareProjectDir(projectDir)

        ignoreDir(project, repoDir, projectDir)
        checkNoSourceRepository(project)
    }

    protected abstract File getRepoDir(String testName)
    protected abstract void prepareRepoDir(File repoDir)
    private static Project prepareProjectDir(File projectDir) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        SourceControlRepositories.createExtension(project)
        return project
    }

    protected void checkInitialState(Project project, File repoDir) {
        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertFalse("Initially there are no local changes", sourceControl.hasLocalChanges())
        assertEquals(
            "The SourceControlRepository reports its directory correctly",
            repoDir.getCanonicalFile(),
            sourceControl.getLocalDir()
        )

        checkInitialState(project, sourceControl)
    }

    protected abstract void checkInitialState(Project project, SourceControlRepository sourceControl)
    protected abstract void addFile(Project project)
    protected abstract void checkStateWithAddedFile(Project project)

    protected abstract void ignoreDir(Project project, File repoDir, File dirToIgnore)

    private static void checkNoSourceRepository(Project project) {
        SourceControlRepository sourceControl =
            project.extensions.findByName("sourceControl") as SourceControlRepository
        assertEquals(
            "${project} sourceControl is of type ${DummySourceControl}",
            DummySourceControl,
            sourceControl.getClass()
        )
    }

}
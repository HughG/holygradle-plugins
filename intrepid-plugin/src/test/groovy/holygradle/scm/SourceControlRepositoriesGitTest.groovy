package holygradle.scm

import holygradle.testUtil.ScmUtil
import org.gradle.api.Project

import java.nio.file.Path

import static org.junit.Assert.*

class SourceControlRepositoriesGitTest extends SourceControlRepositoriesTestBase {

    @Override
    protected File getRepoDir(String testName) {
        return new File(getTestDir(), "testGit" + testName)
    }

    @Override
    protected void prepareRepoDir(File repoDir) {
        ScmUtil.gitExec(repoDir, "init")
    }

    @Override
    protected void checkInitialState(Project project, SourceControlRepository sourceControl) {
        // Add a file.
        (new File(project.projectDir, EXAMPLE_FILE)).text = "ahoy"
        ScmUtil.gitExec(project, "add", EXAMPLE_FILE)
        // Set the commit message, user, and date, so that the hash will be the same every time.
        ScmUtil.gitExec(project,
                        "commit",
                        "-m", "Added another file."
        )

        assertTrue(
            "A GitRepository instance has been created for ${project}",
            sourceControl instanceof GitRepository
        )
        assertEquals("The master repo is ''", "", sourceControl.getUrl())
        assertEquals(
            "The SourceControlRepository reports its protocol correctly",
            "git",
            sourceControl.getProtocol()
        )
    }

    @Override
    protected void addFile(Project project) {
        new File(project.projectDir, EXAMPLE_FILE as String).withPrintWriter {
            PrintWriter w -> w.println("two")
        }
    }

    @Override
    protected void checkStateWithAddedFile(Project project) {
        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertTrue("Local changes are detected correctly", sourceControl.hasLocalChanges())

    }

    @Override
    protected void ignoreDir(Project project, File repoDir, File dirToIgnore) {
        Path relativePathToIgnore = repoDir.toPath().relativize(dirToIgnore.toPath())
        new File(repoDir, ".gitignore").text = "/${relativePathToIgnore}/"
    }
}
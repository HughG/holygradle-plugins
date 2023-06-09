package holygradle.scm

import holygradle.io.FileHelper
import holygradle.testUtil.ScmUtil
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import java.nio.file.Path

import static org.junit.Assert.*
/**
 * Integration test of the HgRepository class.  Most of this test is actually in the "build.gradle" script which is
 * launched.  This is because the test needs to run the version of Mercurial which the intrepid depends on, and the
 * only easy way to do that is to run a build which uses the intrepid plugin.
 */
class SourceControlRepositoriesHgTest extends SourceControlRepositoriesTestBase {

    @Override
    protected File getRepoDir(String testName) {
        return new File(getTestDir(), "testHg" + testName)
    }

    @Override
    protected void prepareRepoDir(File repoDir) {
        ScmUtil.hgExec(repoDir, "init")
    }

    @Override
    protected void checkInitialState(Project project, File repoDir, SourceControlRepository sourceControl) {
        // Add a file.
        (new File(project.projectDir, EXAMPLE_FILE)).text = "ahoy"
        ScmUtil.hgExec(project, "add", EXAMPLE_FILE)
        // Set the commit message, user and date, so that the hash will be the same every time.
        ScmUtil.hgExec(project,
                "commit",
                "-m", "Added another file.",
                "-u", "TestUser",
                "-d", "2000-01-01"
        )

        assertTrue(
            "An HgRepository instance has been created for the project",
            sourceControl instanceof HgRepository
        )

        String expectedHash
        String projectDirName = project.projectDir.name
        switch (projectDirName) {
            case "testHgBU":
                expectedHash = "30c86257cd03bde0acb2e22f91512e589df605e9"
                break
            case "sub":
                expectedHash = "c00b05e262a46c28648da50f19d23014672353bf"
                break
            case "ignored":
                expectedHash = null
                break
            default:
                throw new RuntimeException("Unsupported project dir ${projectDirName}")
        }
        assertEquals(
            "First commit hash is as expected",
            expectedHash,
            sourceControl.getRevision()
        )
        assertEquals("The master repo is 'unknown'", "unknown", sourceControl.getUrl())
        assertEquals(
            "The SourceControlRepository reports its protocol correctly",
            "hg",
            sourceControl.getProtocol()
        )
    }

    @Override
    protected void modifyWorkingCopy(Project project) {
        new File(project.projectDir, EXAMPLE_FILE as String).withPrintWriter {
            PrintWriter w -> w.println("two")
        }
    }

    @Override
    protected void checkStateWithAddedFile(Project project) {
        def sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertTrue("Local changes are detected correctly", sourceControl.hasLocalChanges())
    }

    @Override
    protected void addDir(File repoDir, File dir) {
        new File(dir, "dummy.txt").text = "Dummy file so this folder can be committed."
        ScmUtil.hgExec(dir, "add", ".")
        // Set the commit message, user and date, so that the hash will be the same every time.
        ScmUtil.hgExec(dir,
                "commit", ".",
                "-m", "'Add ${dir}'",
                "-u", "TestUser",
                "-d", "2000-01-01"
        )
    }

    @Override
    protected void ignoreDir(File repoDir, File dirToIgnore) {
        Path relativePathToIgnore = repoDir.toPath().relativize(dirToIgnore.toPath())
        new File(repoDir, ".hgignore").text = "^${relativePathToIgnore}/"
    }

    /**
     * This is a regression test for GR #3780.  The HgRepository class would report the wrong revision for the working
     * copy if it was not the most recent commit in the repo.  (This could be because it wasn't updated to the tip of
     * a branch, or because a more recent commit exists on another branch.)  The bug was that it used "hg log -l 1" to
     * return the node string for the most recent commit, instead of working from "hg id" to get the node of the
     * working copy's parent.
     */
    @Test
    public void testHgGetRevisionWithNewerCommit() {
        ////////////////////////////////////////////////////////////////////////////////
        // Create a repo to run the test in.
        File projectDir = getRepoDir("tGRWNC")
        FileHelper.ensureDeleteDirRecursive(projectDir)
        FileHelper.ensureMkdirs(projectDir)

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        // Make the project dir into a repo, then add the extension.
        ScmUtil.hgExec(project, "init")
        SourceControlRepositories.createExtension(project)


        ////////////////////////////////////////////////////////////////////////////////
        // Now run the actual test
        def sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertTrue(
                "An HgRepository instance has been created for the project",
                sourceControl instanceof HgRepository
        )

        // Add a file.
        (new File(project.projectDir, ".hgignore")).withPrintWriter { PrintWriter w ->
            w.println("^build/")
            w.println("^.*\\.gradle/")
        }
        ScmUtil.hgExec(project, "add", ".hgignore")
        // Set the commit message, user, and date, so that the hash will be the same every time.
        ScmUtil.hgExec(project,
                       "commit",
                       "-m", "Initial test state.",
                       "-u", "TestUser",
                       "-d", "2000-01-01"
        )

        assertEquals(
                "First commit hash is as expected",
                "cd7b5c688d1504b029a7286c2c0124c86b1d39a2",
                sourceControl.getRevision()
        )
        assertFalse("Initially there are no local changes", sourceControl.hasLocalChanges())

        // Add another file.
        (new File(project.projectDir, EXAMPLE_FILE)).text = "ahoy"
        ScmUtil.hgExec(project, "add", EXAMPLE_FILE)
        // Set the commit message, user, and date, so that the hash will be the same every time.
        ScmUtil.hgExec(project,
                       "commit",
                       "-m", "Added another file.",
                       "-u", "TestUser",
                       "-d", "2000-01-02"
        )

        assertEquals(
                "Second commit hash is as expected",
                "2fbc9b5207fda8a526ce38d6bb1ae208b175cd64",
                sourceControl.getRevision()
        )

        ScmUtil.hgExec(project,
                       "update",
                       "-r", "cd7b5c688d1504b029a7286c2c0124c86b1d39a2",
                       )

        assertEquals(
                "Updating to first commit hash is detected as expected",
                "cd7b5c688d1504b029a7286c2c0124c86b1d39a2",
                sourceControl.getRevision()
        )

    }
}
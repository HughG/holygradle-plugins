package holygradle.scm

import holygradle.io.FileHelper
import holygradle.test.*
import holygradle.testUtil.GitUtil
import holygradle.testUtil.HgUtil
import holygradle.testUtil.ZipUtil
import org.gradle.api.Project
import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder

import static org.junit.Assert.*

class SourceControlRepositoriesTest extends AbstractHolyGradleTest {
    private static final String EXAMPLE_FILE = "ahoy.txt"

    @Test
    public void testSvn() {
        File svnDir = ZipUtil.extractZip(getTestDir(), "test_svn")
        
        Project project = ProjectBuilder.builder().withProjectDir(svnDir).build()
        SourceControlRepositories.createExtension(project)
        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository
        
        assertTrue(sourceControl instanceof SvnRepository)
        assertEquals("1", sourceControl.getRevision())
        assertFalse(sourceControl.hasLocalChanges())
        assertEquals("file:///C:/Projects/DependencyManagement/Project/test_svn_repo/trunk", sourceControl.getUrl())
        assertEquals("svn", sourceControl.getProtocol())
        assertEquals(svnDir.getCanonicalFile(), sourceControl.getLocalDir())
        
        File helloFile = new File(svnDir, "hello.txt")
        helloFile.write("bonjour")
        assertTrue(sourceControl.hasLocalChanges())
    }

    /**
     * Integration test of the HgRepository class.  Most of this test is actually in the "build.gradle" script which is
     * launched.  This is because the test needs to run the version of Mercurial which the intrepid depends on, and the
     * only easy way to do that is to run a build which uses the intrepid plugin.
     */
    @Test
    public void testHg() {
        ////////////////////////////////////////////////////////////////////////////////
        // Create a repo to run the test in.
        File projectDir = new File(getTestDir(), "testHg")
        FileHelper.ensureDeleteDirRecursive(projectDir)
        FileHelper.ensureMkdirs(projectDir)

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        // Make the project dir into a repo, then add the extension.
        HgUtil.hgExec(project, "init")
        SourceControlRepositories.createExtension(project)

        // Add a file.
        (new File(project.projectDir, EXAMPLE_FILE)).text = "ahoy"
        HgUtil.hgExec(project, "add", EXAMPLE_FILE)
        // Set the commit message, user, and date, so that the hash will be the same every time.
        HgUtil.hgExec(project,
           "commit",
           "-m", "Added another file.",
           "-u", "TestUser",
           "-d", "2000-01-01"
        )

        ////////////////////////////////////////////////////////////////////////////////
        // Now run the actual test
        def sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertTrue(
            "An HgRepository instance has been created for the project",
            sourceControl instanceof HgRepository
        )

        assertEquals(
            "First commit hash is as expected",
            "30c86257cd03bde0acb2e22f91512e589df605e9",
            sourceControl.getRevision()
        )
        assertFalse("Initially there are no local changes", sourceControl.hasLocalChanges())
        assertEquals("The master repo is 'unknown'", "unknown", sourceControl.getUrl())
        assertEquals(
            "The SourceControlRepository reports its protocol correctly",
            "hg",
            sourceControl.getProtocol()
        )
        assertEquals(
            "The SourceControlRepository reports its directory correctly",
            project.projectDir.getCanonicalFile(),
            sourceControl.getLocalDir()
        )

        new File(project.projectDir, EXAMPLE_FILE as String).withPrintWriter {
            PrintWriter w -> w.println("two")
        }

        assertTrue("Local changes are detected correctly", sourceControl.hasLocalChanges())
    }

    /**
     * Integration test of the GitRepository class.
     */
    @Test
    public void testGit() {
        ////////////////////////////////////////////////////////////////////////////////
        // Create a repo to run the test in.
        File projectDir = new File(getTestDir(), "testGit")
        FileHelper.ensureDeleteDirRecursive(projectDir)
        FileHelper.ensureMkdirs(projectDir)

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        // Make the project dir into a repo, then add the extension.
        GitUtil.gitExec(project, "init")
        SourceControlRepositories.createExtension(project)

        // Add a file.
        (new File(project.projectDir, EXAMPLE_FILE)).text = "ahoy"
        GitUtil.gitExec(project, "add", EXAMPLE_FILE)
        // Set the commit message, user, and date, so that the hash will be the same every time.
        GitUtil.gitExec(project,
                "commit",
                "-m", "Added another file."
        )

        ////////////////////////////////////////////////////////////////////////////////
        // Now run the actual test
        def sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertTrue(
                "A GitRepository instance has been created for the project",
                sourceControl instanceof GitRepository
        )
        assertFalse("Initially there are no local changes", sourceControl.hasLocalChanges())
        println(sourceControl.getUrl())
        assertEquals("The master repo is ''", "", sourceControl.getUrl())
        assertEquals(
                "The SourceControlRepository reports its protocol correctly",
                "git",
                sourceControl.getProtocol()
        )
        assertEquals(
                "The SourceControlRepository reports its directory correctly",
                project.projectDir.getCanonicalFile(),
                sourceControl.getLocalDir()
        )

        new File(project.projectDir, EXAMPLE_FILE as String).withPrintWriter {
            PrintWriter w -> w.println("two")
        }

        assertTrue("Local changes are detected correctly", sourceControl.hasLocalChanges())
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
        File projectDir = new File(getTestDir(), "tGRWNC")
        FileHelper.ensureDeleteDirRecursive(projectDir)
        FileHelper.ensureMkdirs(projectDir)

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        // Make the project dir into a repo, then add the extension.
        HgUtil.hgExec(project, "init")
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
        HgUtil.hgExec(project, "add", ".hgignore")
        // Set the commit message, user, and date, so that the hash will be the same every time.
        HgUtil.hgExec(project,
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
        HgUtil.hgExec(project, "add", EXAMPLE_FILE)
        // Set the commit message, user, and date, so that the hash will be the same every time.
        HgUtil.hgExec(project,
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

        HgUtil.hgExec(project,
           "update",
           "-r", "cd7b5c688d1504b029a7286c2c0124c86b1d39a2",
       )

        assertEquals(
            "Updating to first commit hash is detected as expected",
            "cd7b5c688d1504b029a7286c2c0124c86b1d39a2",
            sourceControl.getRevision()
        )

    }

    @Test
    public void testDummy() {
        File dummyDir = new File(getTestDir(), "dummy")

        Project project = ProjectBuilder.builder().withProjectDir(dummyDir).build()
        SourceControlRepositories.createExtension(project)
        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertNotNull(sourceControl)
        assertTrue(sourceControl instanceof DummySourceControl)
        assertEquals(null, sourceControl.getRevision())
        assertFalse(sourceControl.hasLocalChanges())
        assertEquals(null, sourceControl.getUrl())
        assertEquals("n/a", sourceControl.getProtocol())
        assertEquals(null, sourceControl.getLocalDir())
    }

    @Test
    public void testGetWithoutDummy() {
        File dummyDir = new File(getTestDir(), "dummy")
        Project project = ProjectBuilder.builder().withProjectDir(dummyDir).build()
        SourceControlRepository repo = SourceControlRepositories.create(project, dummyDir)
        assertNull(repo)
    }

}
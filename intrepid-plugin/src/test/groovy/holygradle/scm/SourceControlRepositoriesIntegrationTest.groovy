package holygradle.scm

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

import java.nio.file.Files

import static org.junit.Assert.assertTrue

class SourceControlRepositoriesIntegrationTest extends AbstractHolyGradleIntegrationTest {
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
        if (projectDir.exists()) {
            assertTrue("Deleted pre-existing ${projectDir}", projectDir.deleteDir())
        }
        assertTrue("Created empty ${projectDir}", projectDir.mkdirs())

        // Now copy build files into the test project dir.  (Trying to run a build with a relative path to a build file
        // seems to force it to run in the folder containing the build file, so we have to copy them.)
        [
            new File(getTestDir(), "hgExec.gradle"),
            new File(getTestDir(), "testHg.gradle")
        ].each { File file ->
            Files.copy(file.toPath(), new File(projectDir, file.name).toPath())
        }

        // We set up the repo by launching Gradle, because that means we can use the version of Mercurial which is
        // required by the plugins, which means we don't need to worry about the path to hg.exe.
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("-b", "testHg.gradle")
            launcher.forTasks("setupRepo")
        }

        ////////////////////////////////////////////////////////////////////////////////
        // Now run the actual test
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("-b", "testHg.gradle")
            launcher.forTasks("runTest")
        }
    }

    /**
     * This is a regression test for GR #3780.  The HgRepository class would report the wrong revision for the working
     * copy if it was not the most recent commit in the repo.  (This could be because it wasn't updated to the tip of
     * a branch, or because a more recent commit exists on another branch.)  The bug was that it used "hg log -l 1" to
     * return the node string for the most recent commit, instead of working from "hg id" to get the node of the
     * working copy's parent.
     */
    @Test
    public void testGetRevisionWithNewerCommit() {
        ////////////////////////////////////////////////////////////////////////////////
        // Create a repo to run the test in.
        File projectDir = new File(getTestDir(), "tGRWNC")
        if (projectDir.exists()) {
            assertTrue("Deleted pre-existing ${projectDir}", projectDir.deleteDir())
        }
        assertTrue("Created empty ${projectDir}", projectDir.mkdirs())

        // Now copy build files into the test project dir.  (Trying to run a build with a relative path to a build file
        // seems to force it to run in the folder containing the build file, so we have to copy them.)
        [
            new File(getTestDir(), "hgExec.gradle"),
            new File(getTestDir(), "testGetRevisionWithNewerCommit.gradle")
        ].each { File file ->
            Files.copy(file.toPath(), new File(projectDir, file.name).toPath())
        }

        // We set up the repo by launching Gradle, because that means we can use the version of Mercurial which is
        // required by the plugins, which means we don't need to worry about the path to hg.exe.
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("-b", "testGetRevisionWithNewerCommit.gradle")
            launcher.forTasks("setupRepo")
        }

        ////////////////////////////////////////////////////////////////////////////////
        // Now run the actual test
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("-b", "testGetRevisionWithNewerCommit.gradle")
            launcher.forTasks("runTest")
        }

    }

}
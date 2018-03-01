package holygradle.scm

import holygradle.test.AbstractHolyGradleTest
import holygradle.testUtil.ExecUtil
import org.gradle.process.ExecSpec
import org.junit.Test

import static org.junit.Assert.*

/**
 * Unit test of {@link holygradle.scm.GitRepository}.
 */
class GitRepositoryTest extends AbstractHolyGradleTest {
    @Test
    public void testGetRevision() {
        final String expectedRevision = "12345abcdefg"
        final List<String> expectedArgs  = ["rev-parse", "HEAD"]
        final ExecSpec stubSpec = ExecUtil.makeStubExecSpec()

        final Command gitCommand = [ execute : { Closure configure -> configure(stubSpec); "12345abcdefg"} ] as Command
        final File workingDir = getTestDir()
        final GitRepository repo = new GitRepository(gitCommand, workingDir)
        final String actualRevision = repo.revision

        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Git args", expectedArgs, stubSpec.args)
        assertEquals("Revision", expectedRevision, actualRevision)
    }

    @Test
    public void testHasLocalChanges() {
        final List<String> expectedArgs = ["status", "--porcelain", "--untracked-files=no"]
        Object[] changedFileList = null
        final ExecSpec stubSpec = ExecUtil.makeStubExecSpec()
        final Command gitCommand = [
                execute : { Closure configure ->
                    configure(stubSpec)
                    changedFileList.join("\n")
                }
        ] as Command

        final File workingDir = getTestDir()
        final GitRepository repo = new GitRepository(gitCommand, workingDir)

        changedFileList = [" M some_modified_file.txt"," A some_added_file.foo"]
        assertTrue(repo.hasLocalChanges)
        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Git args", expectedArgs, stubSpec.args)

        changedFileList = []
        assertFalse(repo.hasLocalChanges)
        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Git args", expectedArgs, stubSpec.args)
    }
}
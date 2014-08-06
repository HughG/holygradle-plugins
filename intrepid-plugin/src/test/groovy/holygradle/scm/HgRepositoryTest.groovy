package holygradle.scm

import holygradle.test.AbstractHolyGradleTest
import holygradle.testUtil.ExecUtil
import org.gradle.process.ExecSpec
import org.junit.Test

import static org.junit.Assert.*

/**
 * Unit test of {@link holygradle.scm.HgRepository}.
 */
class HgRepositoryTest extends AbstractHolyGradleTest {
    @Test
    public void testGetRevision() {
        final String expectedRevision = "12345abcdefg"
        final List<String> expectedArgs  = ["log", "-l", "1", "--template", "\"{node}\""]
        final ExecSpec stubSpec = ExecUtil.makeStubExecSpec()

        final HgCommand hgCommand = [ execute : { Closure configure -> configure(stubSpec); "12345abcdefg"} ] as HgCommand
        final File workingDir = getTestDir()
        final HgRepository repo = new HgRepository(hgCommand, null, workingDir)
        final String actualRevision = repo.getRevision()

        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)
        assertEquals("Revision", expectedRevision, actualRevision)
    }

    @Test
    public void testHasLocalChanges() {
        final List<String> expectedArgs = ["status", "-amrdC"]
        Object[] changedFileList = null
        final ExecSpec stubSpec = ExecUtil.makeStubExecSpec()
        final HgCommand hgCommand = [
            execute : { Closure configure ->
                configure(stubSpec)
                changedFileList.join("\n")
            }
        ] as HgCommand

        final File workingDir = getTestDir()
        final HgRepository repo = new HgRepository(hgCommand, null, workingDir)

        changedFileList = ["M some_modified_file.txt","A some_added_file.foo"]
        assertTrue(repo.hasLocalChanges())
        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)

        changedFileList = []
        assertFalse(repo.hasLocalChanges())
        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)
    }
}
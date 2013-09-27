package holygradle.scm

import holygradle.test.*
import holygradle.testUtil.ExecUtil
import holygradle.testUtil.ZipUtil
import org.gradle.api.Project
import org.gradle.process.ExecSpec
import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

/**
 * Unit test of {@link holygradle.scm.HgRepository}.
 */
class HgRepositoryTest extends AbstractHolyGradleTest {
    @Test
    public void testGetRevision() {
        final String expectedRevision = "12345abcdefg"
        final Object[] expectedArgs  = ["log", "-l", "1", "--template", "\"{node}\""]
        final ExecSpec stubSpec = ExecUtil.makeStubExecSpec()

        final HgCommand hgCommand = [ execute : { Closure configure -> configure(stubSpec); "12345abcdefg"} ] as HgCommand
        final File workingDir = new File(getTestDir(), "test_hg")
        final HgRepository repo = new HgRepository(hgCommand, workingDir)
        final String actualRevision = repo.getRevision()

        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)
        assertEquals("Revision", expectedRevision, actualRevision)
    }

    @Test
    public void testHasLocalChanges() {
        final Object[] expectedArgs = ["status", "-amrdC"]
        Object[] changedFileList = null
        final ExecSpec stubSpec = ExecUtil.makeStubExecSpec()
        final HgCommand hgCommand = [
            execute : { Closure configure ->
                configure(stubSpec)
                changedFileList.join("\n")
            }
        ] as HgCommand

        final File workingDir = new File(getTestDir(), "test_hg")
        final HgRepository repo = new HgRepository(hgCommand, workingDir)

        changedFileList = ["M some_modified_file.txt","A some_added_file.foo"]
        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)
        assertTrue(repo.hasLocalChanges())

        changedFileList = []
        assertEquals("Working dir", workingDir, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)
        assertFalse(repo.hasLocalChanges())
    }
}
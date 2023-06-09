package holygradle.scm

import holygradle.test.AbstractHolyGradleTest
import holygradle.testUtil.ExecUtil
import org.gradle.api.Action
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
        final List<String> expectedArgs  = ["log", "-r", "12345abcdefg", "-l", "1", "--template", "\"{node}\""]
        final ExecSpec stubSpec = ExecUtil.makeStubExecSpec()

        final Command hgCommand = [
                execute : { Action<ExecSpec> configure ->
                    configure.execute(stubSpec)
                    "12345abcdefg"
                }
        ] as Command
        final File workingDir = getTestDir()
        final HgRepository repo = new HgRepository(hgCommand, workingDir)
        final String actualRevision = repo.revision

        assertEquals("Working dir", workingDir.absoluteFile, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)
        assertEquals("Revision", expectedRevision, actualRevision)
    }

    @Test
    public void testHasLocalChanges() {
        final List<String> expectedArgs = ["status", "-amrdC"]
        Object[] changedFileList = null
        final ExecSpec stubSpec = ExecUtil.makeStubExecSpec()
        final Command hgCommand = [
            execute : { Action<ExecSpec> configure ->
                configure.execute(stubSpec)
                changedFileList.join("\n")
            }
        ] as Command

        final File workingDir = getTestDir()
        final HgRepository repo = new HgRepository(hgCommand, workingDir)

        changedFileList = ["M some_modified_file.txt","A some_added_file.foo"]
        assertTrue(repo.hasLocalChanges)
        assertEquals("Working dir", workingDir.absoluteFile, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)

        changedFileList = []
        assertFalse(repo.hasLocalChanges)
        assertEquals("Working dir", workingDir.absoluteFile, stubSpec.workingDir)
        assertEquals("Hg args", expectedArgs, stubSpec.args)
    }
}
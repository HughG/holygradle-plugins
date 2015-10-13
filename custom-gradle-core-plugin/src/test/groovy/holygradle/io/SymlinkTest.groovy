package holygradle.io

import holygradle.test.AbstractHolyGradleTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.junit.Assert.*

class SymlinkTest extends AbstractHolyGradleTest
{
    @Rule public ExpectedException thrown = ExpectedException.none();

    /**
     * Test that an exception is thrown if you try to create a symlink to a non-existent target.
     *
     * This is a regression test for behaviour intentionally changed.  The Symlink class used to always create a
     * directory or file symlink as directed, even if the target didn't exist.  Calling code partly relied on this
     * behaviour, just printing a warning and continuing, but this sometimes masked genuine problems.  Calling code
     * has been rewritten to ensure that targets are always created before symlinks, so that we can spot real bugs.
     */
    @Test
    public void testCreateLinkToMissingDirectory() {
        File link = new File(testDir, "link_to_missing")
        File missingDir = new File("missing_folder")
        if (link.exists()) {
            fail("Test pre-condition: ${link} should not exist")
        }
        if (missingDir.exists()) {
            fail("Test pre-condition: ${missingDir} should not exist")
        }

        thrown.expect(IOException)
        thrown.expectMessage("Cannot create link to non-existent target")
        Symlink.rebuild(link, missingDir)
    }
}

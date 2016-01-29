package holygradle.io

import holygradle.custom_gradle.util.RetryHelper
import holygradle.test.AbstractHolyGradleTest
import org.eclipse.jdt.internal.core.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.junit.Assert.*

abstract class LinkTestBase extends AbstractHolyGradleTest
{
    @Rule public ExpectedException thrown = ExpectedException.none();

    protected abstract void makeLinkExternally(File link, File target)

    protected abstract boolean isLink(File link)

    protected abstract void rebuild(File link, File target)

    protected abstract void delete(File link)

    /**
     * Test that you can create a link to a target which exists, using an absolute path.
     */
    @Test
    public void testLinkIsDetectedCorrectly() {
        File link = new File(testDir, "link_to_existing")
        File existingDir = new File(testDir, "existing_folder")
        if (link.exists()) {
            Link.delete(link)
            FileHelper.ensureDeleteFile(link, "for test setup")
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            fail("Test pre-condition: '${existingDir}' should not exist, or should be a directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")
        makeLinkExternally(link, existingDir)

        Assert.isTrue(isLink(link), "Detected '$link' as link")
    }

    /**
     * Test that you can create a link to a target which exists, using an absolute path.
     */
    @Test
    public void testCreateLinkToAbsolutePathToExistingDirectory() {
        File link = new File(testDir, "link_to_existing")
        File existingDir = new File(testDir, "existing_folder")
        if (link.exists()) {
            Link.delete(link)
            FileHelper.ensureDeleteFile(link, "for test setup")
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            fail("Test pre-condition: '${existingDir}' should not exist, or should be a directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")

        rebuild(link, existingDir)
    }

    /**
     * Test that you can create a link to a target which exists, using a relative path.
     */
    @Test
    public void testCreateLinkToRelativePathToExistingDirectory() {
        File link = new File(testDir, "link_to_existing")
        File existingDir = new File("../${testDir.name}/existing_folder")
        if (link.exists()) {
            Link.delete(link)
            FileHelper.ensureDeleteFile(link, "for test setup")
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            fail("Test pre-condition: '${existingDir}' should not exist, or should be a directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")

        rebuild(link, existingDir)
    }

    /**
     * Test that an exception is thrown if you try to create a link to a non-existent target.
     *
     * This is a regression test for behaviour intentionally changed.  The Symlink class used to always create a
     * directory or file symlink as directed, even if the target didn't exist.  Calling code partly relied on this
     * behaviour, just printing a warning and continuing, but this sometimes masked genuine problems.  Calling code
     * has been rewritten to ensure that targets are always created before symlinks, so that we can spot real bugs.
     */
    @Test
    public void testCreateLinkToMissingDirectory() {
        File link = new File(testDir, "link_to_missing")
        File missingDir = new File(testDir, "missing_folder")
        if (link.exists()) {
            Link.delete(link)
            FileHelper.ensureDeleteFile(link, "for test setup")
        }
        if (missingDir.exists()) {
            fail("Test pre-condition: '${missingDir}' should not exist")
        }
        // File deletion seems to happen after the JDK method returns, sometimes, so check until it's really gone.
        RetryHelper.retry(10, 1000, null, "Check ${missingDir.name} dir was really deleted") {
            !missingDir.exists()
        }

        thrown.expect(IOException)
        thrown.expectMessage("Cannot create link to non-existent target")
        rebuild(link, missingDir)
    }

    /**
     * Test that you can delete a link whose target exists, and then the target still exists.
     */
    @Test
    public void testDeleteLink() {
        File link = new File(testDir, "link_to_existing")
        File existingDir = new File(testDir, "existing_folder")
        if (link.exists()) {
            Link.delete(link)
            FileHelper.ensureDeleteFile(link, "for test setup")
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            fail("Test pre-condition: '${existingDir}' should not exist, or should be a directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")
        rebuild(link, existingDir)

        // Now for the actual test.
        delete(link)
        if (!existingDir.exists()) {
            fail("Link '${link}' target '${existingDir}' should still exist after deleting link")
        }
        if (!existingDir.isDirectory()) {
            fail("Link '${link}' target '${existingDir}' still exists after deleting link, but is no longer a directory!")
        }
    }

    /**
     * Test that you can delete a link whose target does not exist.
     */
    @Test
    public void testDeleteLinkWithMissingTarget() {
        File link = new File(testDir, "link_to_existing")
        File existingDir = new File(testDir, "existing_folder")
        if (link.exists()) {
            Link.delete(link)
            FileHelper.ensureDeleteFile(link, "for test setup")
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            fail("Test pre-condition: '${existingDir}' should not exist, or should be an empty directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")
        rebuild(link, existingDir)
        FileHelper.ensureDeleteDirRecursive(existingDir, "for test setup")
        // File deletion seems to happen after the JDK method returns, sometimes, so check until it's really gone.
        RetryHelper.retry(10, 1000, null, "Check ${existingDir.name} dir was really deleted") {
            !existingDir.exists()
        }

        // Now for the actual test.
        delete(link)
        if (existingDir.exists()) {
            fail("Link '${link}' target '${existingDir}' should still not exist")
        }
    }
}

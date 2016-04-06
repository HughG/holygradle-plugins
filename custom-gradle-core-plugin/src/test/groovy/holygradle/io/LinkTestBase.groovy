package holygradle.io

import holygradle.custom_gradle.util.RetryHelper
import holygradle.test.AbstractHolyGradleTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/*
    NOTE: On Windows 8 and above, the SymlinkTest subclass needs to be run with Administrator privileges if the current
    user is a member of the Administrators group.  If you run these tests from a non-admin command prompt, you'll get
    an exception like the following.

        java.nio.file.FileSystemException: <filename>: A required privilege is not held by the client
*/

abstract class LinkTestBase extends AbstractHolyGradleTest
{
    @Rule public ExpectedException thrown = ExpectedException.none();

    protected abstract void makeLinkExternally(File link, File target)

    protected abstract boolean isLink(File link)

    protected abstract void rebuild(File link, File target)

    protected abstract void delete(File link)

    protected abstract File getTarget(File link)

    /**
     * Test that you can create a link to a target which exists, using an absolute path.
     */
    @Test
    public void testLinkIsDetectedCorrectly() {
        // Arrange
        File link = new File(testDir, "link_to_existing").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)
        File existingDir = new File(testDir, "existing_folder").canonicalFile
        Assert.assertTrue("target '$existingDir' is absolute", existingDir.absolute)
        if (link.exists()) {
            Link.delete(link)
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            Assert.fail("Test pre-condition: '${existingDir}' should not exist, or should be a directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")

        // Act
        makeLinkExternally(link, existingDir)

        // Assert
        Assert.assertTrue("Detected '$link' as link", isLink(link))
        Assert.assertEquals("Target of '$link'", existingDir.canonicalFile, getTarget(link).canonicalFile)
    }

    /**
     * Test that you can create a link to a target which exists, using an absolute path.
     */
    @Test
    public void testCreateLinkToAbsolutePathToExistingDirectory() {
        // Arrange
        File link = new File(testDir, "link_to_existing").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)
        File existingDir = new File(testDir, "existing_folder").canonicalFile
        Assert.assertTrue("target '$existingDir' is absolute", existingDir.absolute)
        if (link.exists()) {
            Link.delete(link)
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            Assert.fail("Test pre-condition: '${existingDir}' should not exist, or should be a directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")


        // Act
        rebuild(link, existingDir)

        // Assert
        File target = getTarget(link)
        Assert.assertEquals("Target of '$link'", existingDir.canonicalFile, target.canonicalFile)
    }

    /**
     * Test that you can create a link to a target which exists, using a relative path.
     */
    @Test
    public void testCreateLinkToRelativePathToExistingDirectory() {
        // Arrange
        File link = new File(testDir, "link_to_existing").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)
        File existingDir = new File("../${testDir.name}/existing_folder")
        Assert.assertTrue("target '$existingDir' is NOT absolute", !existingDir.absolute)
        if (link.exists()) {
            Link.delete(link)
        }
        File canonicalTarget = getCanonicalLinkTargetPath(link, existingDir)
        if (canonicalTarget.exists() && !canonicalTarget.isDirectory()) {
            Assert.fail("Test pre-condition: '${canonicalTarget}' should not exist, or should be a directory")
        }
        FileHelper.ensureMkdirs(canonicalTarget, "for test setup")

        // Act
        rebuild(link, existingDir)

        // Assert
        File target = getTarget(link)
        File canonicalTargetFromLink = getCanonicalLinkTargetPath(link, target)
        Assert.assertEquals("Target of '$link'", canonicalTarget, canonicalTargetFromLink)
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
        // Arrange
        File link = new File(testDir, "link_to_missing").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)
        File missingDir = new File(testDir, "missing_folder").canonicalFile
        Assert.assertTrue("target '$missingDir' is absolute", missingDir.absolute)
        if (link.exists()) {
            Link.delete(link)
        }
        if (missingDir.exists()) {
            Assert.fail("Test pre-condition: '${missingDir}' should not exist")
        }
        // File deletion seems to happen after the JDK method returns, sometimes, so check until it's really gone.
        RetryHelper.retry(10, 1000, null, "Check ${missingDir.name} dir was really deleted") {
            !missingDir.exists()
        }

        // Assert (written early because of how JUnit asserts about exceptions)
        thrown.expect(IOException)
        thrown.expectMessage("Cannot create link to non-existent target")

        // Act
       rebuild(link, missingDir)
    }

    /**
     * Test that you can delete a link whose target exists, and then the target still exists.
     */
    @Test
    public void testDeleteLink() {
        // Arrange
        File link = new File(testDir, "link_to_existing").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)
        File existingDir = new File(testDir, "existing_folder").canonicalFile
        Assert.assertTrue("target '$existingDir' is absolute", existingDir.absolute)
        if (link.exists()) {
            Link.delete(link)
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            Assert.fail("Test pre-condition: '${existingDir}' should not exist, or should be a directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")
        rebuild(link, existingDir)

        // Act
        delete(link)

        // Assert
        if (!existingDir.exists()) {
            Assert.fail("Link '${link}' target '${existingDir}' should still exist after deleting link")
        }
        if (!existingDir.isDirectory()) {
            Assert.fail(
                "Link '${link}' target '${existingDir}' still exists after deleting link, but is no longer a directory!"
            )
        }
    }

    /**
     * Test that you can delete a link whose target does not exist.
     */
    @Test
    public void testDeleteLinkWithMissingTarget() {
        // Arrange
        File link = new File(testDir, "link_to_existing").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)
        File existingDir = new File(testDir, "existing_folder").canonicalFile
        Assert.assertTrue("target '$existingDir' is absolute", existingDir.absolute)
        if (link.exists()) {
            Link.delete(link)
        }
        if (existingDir.exists() && !existingDir.isDirectory()) {
            Assert.fail("Test pre-condition: '${existingDir}' should not exist, or should be an empty directory")
        }
        FileHelper.ensureMkdirs(existingDir, "for test setup")
        rebuild(link, existingDir)
        FileHelper.ensureDeleteDirRecursive(existingDir, "for test setup")
        // File deletion seems to happen after the JDK method returns, sometimes, so check until it's really gone.
        RetryHelper.retry(10, 1000, null, "Check ${existingDir.name} dir was really deleted") {
            !existingDir.exists()
        }

        // Act

        // Assert
        delete(link)
        if (existingDir.exists()) {
            Assert.fail("Link '${link}' target '${existingDir}' should still not exist")
        }
    }

    /**
     * Directory junctions always use an absolute path internally but symlinks can use a relative path.  For test
     * comparisons and for making target folders, we always want the absolute target path.
     */
    private static File getCanonicalLinkTargetPath(File link, File target) {
        if (target.isAbsolute()) {
            return target.canonicalFile
        } else {
            // If [target] is relative, we want createSymbolicLink to create a link relative to [link] (as opposed to
            // relative to the current working directory) so we have to calculate this.
            File canonicalLink = link.canonicalFile
            return new File(canonicalLink.parentFile, target.path).canonicalFile
        }
    }
}

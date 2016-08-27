package holygradle.io

import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.junit.Assert
import org.junit.Test

import java.nio.file.Files

/*
    NOTE: On Windows 8 and above, the SymlinkTest subclass needs to be run with Administrator privileges if the current
    user is a member of the Administrators group.  If you run these tests from a non-admin command prompt, you'll get
    an exception like the following.

        java.nio.file.FileSystemException: <filename>: A required privilege is not held by the client
*/

class LinkTest extends AbstractHolyGradleTest {
    static String SHARE_NAME = "gradle_test"

    @Test
    public void testNetworkSharesFallbackToSymlink() {
        // Arrange

        // Delete the share in case it exists
        "net share ${SHARE_NAME} /DELETE".execute().waitForProcessOutput()

        // Create a temporary directory and share
        File tempDir = Files.createTempDirectory("holygradle_io_LinkTest").toFile()
        "net share ${SHARE_NAME}=${tempDir.canonicalPath}".execute().waitForProcessOutput()

        File target = new File("\\\\localhost\\${SHARE_NAME}")
        File link = new File(testDir, "linkA").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)
        Assert.assertTrue("Error creating test share ${target}.", target.exists())

        // Make sure there's no existing link.
        if (link.exists()) {
            Link.delete(link)
        }

        // Act
        Link.rebuild(link, target)

        // Assert
        Assert.assertTrue("Link exists", link.exists())
        Assert.assertTrue("Link is symlink", Symlink.isSymlink(link))
    }

    @Test
    public void testSymlinkIsRecreatedAsJunction() {
        // Arrange

        File target = new File(testDir, "target").canonicalFile
        Assert.assertTrue("target '$target' is absolute", target.absolute)
        File link = new File(testDir, "linkB").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)

        if (link.exists()) {
            Link.delete(link)
        }
        FileHelper.ensureMkdirs(target)

        Symlink.rebuild(link, target)

        // Act
        Link.rebuild(link, target)

        // Assert
        Assert.assertTrue("${link} should be a directory junction after rebuild.", Junction.isJunction(link))
    }

    @Test(expected = IOException.class)
    public void testFallbackToSymlinkIsLogged() {
        // Arrange

        File target = new File("C:\\directory\\that\\doesnt\\exist\\hopefully\\").canonicalFile
        Assert.assertTrue("target '$target' is absolute", target.absolute)
        File link = new File(testDir, "linkC").canonicalFile
        Assert.assertTrue("link '$link' is absolute", link.absolute)

        Link.delete(link)

        Logger logger = Logging.getLogger(Link.class)

        // Act
        Link.rebuild(link, target)

        // Assert
        Assert.assertTrue(logger.toString().contains(
            "Failed to create a directory junction from '${link.toString()}' to '${target.toString()}'. " +
            "Falling back to symlinks."
        ))
        Assert.assertTrue(logger.toString().contains(
            "Directory junction and symlink creation failed from '${link.toString()}' to '${target.toString()}'."
        ))
    }
}

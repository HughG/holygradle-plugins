package holygradle.io

import com.google.common.io.Files
import holygradle.test.AbstractHolyGradleTest
import org.eclipse.jdt.internal.core.Assert
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.junit.Test

class LinkTest extends AbstractHolyGradleTest {
    static String SHARE_NAME = "gradle_test"

    @Test
    public void testNetworkSharesFallbackToSymlink() {
        // Delete the share in case it exists
        "net share ${SHARE_NAME} /DELETE".execute()

        // Create a temporary directory and share
        File tempDir = Files.createTempDir()
        "net share ${SHARE_NAME}=${tempDir.canonicalPath}".execute()

        File target = new File("\\\\localhost\\${SHARE_NAME}")
        File link = new File(testDir, "linkA")

        Assert.isTrue(target.exists(), "Error creating test share ${target}")

        Link.delete(link)

        Link.rebuild(link, target, null)
        Assert.isTrue(link.exists())
        Assert.isTrue(Symlink.isSymlink(link))
    }

    @Test
    public void testSymlinkIsRecreatedAsJunction() {
        File target = new File(testDir, "target")
        File link = new File(testDir, "linkB")

        if (link.exists()) {
            Link.delete(link)
        }
        FileHelper.ensureMkdirs(target)

        Symlink.rebuild(link, target)

        Link.rebuild(link, target, null)

        Assert.isTrue(Junction.isJunction(link), "${link} should be a directory junction after rebuild.")
    }

    @Test(expected = IOException.class)
    public void fallbackToSymlinkIsLogged() {
        File target = new File("C:\\directory\\that\\doesnt\\exist\\hopefully\\")
        File link = new File(testDir, "linkC")

        Link.delete(link)

        def logger = Logging.getLogger(this.class)

        Link.rebuild(link, target, logger)

        Assert.isTrue(logger.toString().contains("Failed to create a directory junction from ${link.toString()} to ${target.toString()}. Falling back to symlinks."))
        Assert.isTrue(logger.toString().contains("Directory junction and symlink creation failed."))
    }
}

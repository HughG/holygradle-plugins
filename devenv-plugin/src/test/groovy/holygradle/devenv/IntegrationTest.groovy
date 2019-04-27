package holygradle.devenv

import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

import static org.junit.Assert.assertTrue

class IntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testVs141MultiPlatform() {
        File projectDir = new File(getTestDir(), "multi_platform")
        File libDir = new File(projectDir, "build/lib")
        FileHelper.ensureDeleteDirRecursive(libDir)
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("buildRelease", "buildDebug")
        }
        ["Release", "Debug"].each { String conf ->
            ["64", "32"].each { String platform ->
                final File file = new File(libDir, "${conf}/foo_${conf[0].toLowerCase()}${platform}.lib")
                assertTrue("${file} exists", file.exists())
            }
        }
    }
}

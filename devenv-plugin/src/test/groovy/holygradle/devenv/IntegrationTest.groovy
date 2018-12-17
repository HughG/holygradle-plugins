package holygradle.devenv

import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

import static org.junit.Assert.assertTrue

class IntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testVs141MultiPlatform() {
        File projectDir = new File(getTestDir(), "vs141_multi_platform")
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
    
    @Test
    public void testMultiCompiler() { 
        File projectDir = new File(getTestDir(), "multi_compiler")
        File libDir = new File(projectDir, "build/lib")
        FileHelper.ensureDeleteDirRecursive(libDir)
        // Not implemented this feature yet.
        /*
        invokeGradle(projectDir).forTasks("buildRelease", "buildDebug")
        assertTrue(new File(libDir, "vc10/Debug/foo_vc10_d32.lib").exists())
        assertTrue(new File(libDir, "vc10/Debug/foo_vc10_d64.lib").exists())
        assertTrue(new File(libDir, "vc10/Release/foo_vc10_r64.lib").exists())
        assertTrue(new File(libDir, "vc10/Release/foo_vc10_r64.lib").exists())
        assertTrue(new File(libDir, "vc9/Debug/foo_vc9.lib").exists())
        assertTrue(new File(libDir, "vc9/Release/foo_vc9.lib").exists())
        */
    }
}

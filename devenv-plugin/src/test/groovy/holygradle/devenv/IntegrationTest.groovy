package holygradle.devenv

import holygradle.*
import holygradle.test.*
import org.junit.Test
import static org.junit.Assert.*

class IntegrationTest extends TestBase {
    @Test
    public void testVc10MultiPlatform() { 
        def projectDir = new File(getTestDir(), "vc10_multi_platform")
        def libDir = new File(projectDir, "build/lib")
        if (libDir.exists()) {
            libDir.deleteDir()
        }
        invokeTask(projectDir, "buildRelease", "buildDebug")
        assertTrue(new File(libDir, "Debug/foo_d32.lib").exists())
        assertTrue(new File(libDir, "Debug/foo_d64.lib").exists())
        assertTrue(new File(libDir, "Release/foo_r64.lib").exists())
        assertTrue(new File(libDir, "Release/foo_r64.lib").exists())
    }
}

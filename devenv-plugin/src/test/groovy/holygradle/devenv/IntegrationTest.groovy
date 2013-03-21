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
    
    @Test
    public void testMultiCompiler() { 
        def projectDir = new File(getTestDir(), "multi_compiler")
        def libDir = new File(projectDir, "build/lib")
        if (libDir.exists()) {
            libDir.deleteDir()
        }
        // Not implemented this feature yet.
        /*
        invokeTask(projectDir, "buildRelease", "buildDebug")
        assertTrue(new File(libDir, "vc10/Debug/foo_vc10_d32.lib").exists())
        assertTrue(new File(libDir, "vc10/Debug/foo_vc10_d64.lib").exists())
        assertTrue(new File(libDir, "vc10/Release/foo_vc10_r64.lib").exists())
        assertTrue(new File(libDir, "vc10/Release/foo_vc10_r64.lib").exists())
        assertTrue(new File(libDir, "vc9/Debug/foo_vc9.lib").exists())
        assertTrue(new File(libDir, "vc9/Release/foo_vc9.lib").exists())
        */
    }
}

package holygradle.devenv

import holygradle.test.AbstractHolyGradleIntegrationTest
import org.gradle.tooling.BuildLauncher
import org.junit.Test

import static org.junit.Assert.assertTrue

class IntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testVc10MultiPlatform() { 
        File projectDir = new File(getTestDir(), "vc10_multi_platform")
        File libDir = new File(projectDir, "build/lib")
        if (libDir.exists()) {
            libDir.deleteDir()
        }
        invokeGradle(projectDir) { BuildLauncher launcher ->
            launcher.forTasks("buildRelease", "buildDebug")
        }
        assertTrue(new File(libDir, "Debug/foo_d32.lib").exists())
        assertTrue(new File(libDir, "Debug/foo_d64.lib").exists())
        assertTrue(new File(libDir, "Release/foo_r64.lib").exists())
        assertTrue(new File(libDir, "Release/foo_r64.lib").exists())
    }
    
    @Test
    public void testMultiCompiler() { 
        File projectDir = new File(getTestDir(), "multi_compiler")
        File libDir = new File(projectDir, "build/lib")
        if (libDir.exists()) {
            libDir.deleteDir()
        }
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

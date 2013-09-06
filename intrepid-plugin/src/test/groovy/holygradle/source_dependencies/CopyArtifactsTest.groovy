package holygradle.source_dependencies

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

import static org.junit.Assert.assertTrue

class CopyArtifactsTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testCopyFromPackedDependency() {
        File projectDir = new File(getTestDir(), "copyPackedDependencies")
        File testDir = new File(projectDir, "blah")
        if (testDir.exists()) {
            testDir.deleteDir()
        }
        
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("copyArtifacts")
            launcher.withArguments("-DcopyArtifactsTarget=${testDir.canonicalPath}")
        }
        
        assertTrue(testDir.exists())
        assertTrue(new File(testDir, "Thing.h").exists())
        assertTrue(new File(testDir, "Thing.cpp").exists())
    }
}

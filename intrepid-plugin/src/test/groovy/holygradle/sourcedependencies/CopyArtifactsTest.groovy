package holygradle.sourcedependencies

import holygradle.*
import holygradle.test.*
import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import org.gradle.api.tasks.Upload
import static org.junit.Assert.*

class CopyArtifactsTest extends TestBase {
    @Test
    public void testCopyFromPackedDependency() {
        def projectDir = new File(getTestDir(), "copyPackedDependencies")
        def testDir = new File(projectDir, "blah")
        if (testDir.exists()) {
            testDir.deleteDir()
        }
        
        invokeGradle(projectDir) {
            forTasks("copyArtifacts")
            withArguments("-DcopyArtifactsTarget=${testDir.canonicalPath}")
        }
        
        assertTrue(testDir.exists())
        assertTrue(new File(testDir, "Thing.h").exists())
        assertTrue(new File(testDir, "Thing.cpp").exists())
    }
}

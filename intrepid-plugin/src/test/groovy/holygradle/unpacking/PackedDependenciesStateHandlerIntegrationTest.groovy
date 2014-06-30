package holygradle.unpacking

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Ignore
import org.junit.Test

import static org.junit.Assert.assertTrue

class PackedDependenciesStateHandlerIntegrationTest extends AbstractHolyGradleIntegrationTest {
    /**
     * Tests that an indirect dependency will be fetched, even if it is reached only via a configuration which has no
     * artifacts.
     */
    @Test
    public void useModuleViaEmptyConfig() {
        final File projectDir = new File(getTestDir(), "useModuleViaEmptyConfig")
        final File extLibDir = new File(projectDir, "extlib")
        if (extLibDir.exists()) {
            assertTrue("Removed existing transitive packed dep symlink ${extLibDir}", extLibDir.delete())
        }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("fAD")
        }

        assertTrue("Symlink to transitive packed dep has been created at ${extLibDir}", extLibDir.exists())
    }
}

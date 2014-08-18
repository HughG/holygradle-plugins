package holygradle.unpacking

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Ignore
import org.junit.Test

import static org.junit.Assert.assertTrue

class PackedDependenciesStateHandlerIntegrationTest extends AbstractHolyGradleIntegrationTest {
    /**
     * Tests that indirect dependencies will be fetched, even if they is reached only via configurations which have no
     * artifacts, and even if they are reached via different configurations.  (The latter check is because at one point
     * the code only fetched such dependencies via the first destination configuration encountered from a given origin
     * configuration.)
     */
    @Test
    public void useModulesViaEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useModulesViaEmptyConfigs")
        final File extLibDir = new File(projectDir, "extlib")
        final File anotherLibDir = new File(projectDir, "anotherlib")
        [extLibDir, anotherLibDir].each {
            if (it.exists()) {
                assertTrue("Removed existing transitive packed dep symlink ${it}", it.delete())
            }
        }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("fAD")
        }

        [extLibDir, anotherLibDir].each {
            assertTrue("Symlink to transitive packed dep has been created at ${it}", it.exists())
        }
    }
}

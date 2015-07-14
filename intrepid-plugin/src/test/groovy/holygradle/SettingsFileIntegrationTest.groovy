package holygradle

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

class SettingsFileIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testSubprojectsWithoutBuildFiles() {
        invokeGradle(new File(getTestDir(), "withoutBuildScripts")) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }
    }

}

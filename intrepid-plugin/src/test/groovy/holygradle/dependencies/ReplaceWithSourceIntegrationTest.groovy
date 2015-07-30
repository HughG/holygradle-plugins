package holygradle.dependencies

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Assert
import org.junit.Test

/**
 * Created by pega on 22/07/2015.
 */
class ReplaceWithSourceIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void rootProjectSourceReplacement() {
        File root_directory = new File(getTestDir(), "root_project")
        invokeGradle(root_directory) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        File framework_directory = new File(root_directory, "framework")
        File source_file = new File(framework_directory, "a_source_file.txt")

        Assert.assertTrue(source_file.exists())
    }
}

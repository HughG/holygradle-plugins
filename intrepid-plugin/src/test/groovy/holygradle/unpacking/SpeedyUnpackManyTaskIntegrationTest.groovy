package holygradle.unpacking

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

class SpeedyUnpackManyTaskIntegrationTest extends AbstractHolyGradleIntegrationTest{

    @Test
    void testUnpackLongFilePath() {
        invokeGradle(new File(getTestDir(), "unpackLongFileName")) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")

            //launcher.addArguments("--debug")

            // Test was originally intended to catch failures relating to long file paths in order to fail more informatively,
            // however cannot reproduce this problem. If the issue arises again this can be added in to test the plugin fails
            // appropriately.
            /*
            launcher.expectFailure(
                    "Some error message about the path being too long"
            )
            */
        }
    }
}


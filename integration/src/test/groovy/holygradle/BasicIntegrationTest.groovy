package holygradle

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

/**
 * Very basic integration "smoke test".
 */
class BasicIntegrationTest extends AbstractHolyGradleIntegrationTest {
    /**
     * This is a basic smoke test, just to make sure the plugins initialize correctly when all used together, and
     * produce the expected list of tasks.
     */
    @Test
    public void testAllPluginsInitialiseTogether() {
        final File projectDir = getTestDir()
        final File testStdOutFile = regression.getTestFile("testAllPluginsInitialiseTogether_stdout")
        final File testStdErrFile = regression.getTestFile("testAllPluginsInitialiseTogether_stderr")
        final OutputStream testStdOutOutputStream = new FileOutputStream(testStdOutFile)
        final OutputStream testStdErrOutputStream = new FileOutputStream(testStdErrFile)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks")
                .withArguments("--all")
                .setStandardOutput(testStdOutOutputStream)
                .setStandardError(testStdErrOutputStream)
        }

        // Before we do the regression comparison, re-write any parts of the test files which change per-user or -run.
        String[] testNames = ["testAllPluginsInitialiseTogether_stdout", "testAllPluginsInitialiseTogether_stderr"]
        testNames.each { String testName ->
            regression.replacePatterns(testName, [
                (~/pluginsRepoOverride=.*/) : "pluginsRepoOverride=[active]",
                (~/\w+-SNAPSHOT/) : "SNAPSHOT",
                (~/Total time:.*/) : "Total time: [snipped]"
            ])
            regression.checkForRegression(testName)
        }
    }
}

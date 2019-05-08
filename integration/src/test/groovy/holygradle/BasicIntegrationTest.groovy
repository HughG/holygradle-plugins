package holygradle

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

/**
 * Very basic integration "smoke test".
 */
class BasicIntegrationTest extends AbstractHolyGradleIntegrationTest {
    /*
     * Helper method to regression-test both stdout and stderr, ignoring uninteresting variations.
     */
    private void compareBuildOutput(String testName, Closure configureLauncher) {
        final File projectDir = new File(getTestDir(), testName)
        final File testStdOutFile = regression.getTestFile("${testName}_stdout")
        final File testStdErrFile = regression.getTestFile("${testName}_stderr")
        final OutputStream testStdOutOutputStream = new FileOutputStream(testStdOutFile)
        final OutputStream testStdErrOutputStream = new FileOutputStream(testStdErrFile)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            configureLauncher(launcher)
            launcher
                .setStandardOutput(testStdOutOutputStream)
                .setStandardError(testStdErrOutputStream)
        }

        // Before we do the regression comparison, re-write any parts of the test files which change per-user or -run.
        String[] testNames = ["${testName}_stdout", "${testName}_stderr"]
        testNames.each { String testFileName ->
            regression.replacePatterns(testFileName, [
                (~/^Detected a changing module.*$/) : null,
                (~/pluginsRepoOverride=.*/) : "pluginsRepoOverride=[active]",
                (~/Download .*\.(pom|jar)/) : null, // Ignore any new Holy Gradle dependencies being cached.
                (~/BUILD SUCCESSFUL in [0-9]+s*/) : "BUILD SUCCESSFUL in [snipped]s",
                (~/Gradle user home:.*/) : "Gradle user home: [snipped]",
                (~/Holy Gradle init script .*/) : "Holy Gradle init script [snipped]",
                (~/Using SNAPSHOT version\..*/) : null,
                (~/ UP-TO-DATE$/) : "",
                (~/the test executable was specified as '.*holy-gradle-plugins/) :
                        "the test executable was specified as '...holy-gradle-plugins"
            ])
            regression.checkForRegression(testFileName)
        }
    }

    /**
     * This is a basic smoke test, just to make sure the plugins initialize correctly when all used together, and
     * produce the expected list of tasks.
     */
    @Test
    public void testAllPluginsInitialiseTogether() {
        compareBuildOutput("tAPIT") { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks", "--all")
        }
    }

    /**
     * This tests that all the plugins correctly evaluate some basic DSL usage.  It doesn't run plugin-specific tasks.
     */
    @Test
    public void testBasicPluginConfig() {
        compareBuildOutput("tBPC") { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks", "--all")
        }
    }
}

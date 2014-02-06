package holygradle

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Ignore
import org.junit.Test

import java.nio.file.Files
import java.nio.file.Paths

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
                (~/Total time:.*/) : "Total time: [snipped]"
            ])
            regression.checkForRegression(testFileName)
        }
    }

    /**
     * This is a basic smoke test, just to make sure the plugins initialize correctly when all used together, and
     * produce the expected list of tasks.
     */
    @Test
    @Ignore
    public void testAllPluginsInitialiseTogether() {
        compareBuildOutput("tAPIT") { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks")
                .withArguments("--all")
        }
    }

    /**
     * This tests that all the plugins correctly evaluate some basic DSL usage.  It doesn't run plugin-specific tasks.
     */
    @Test
    @Ignore
    public void testBasicPluginConfig() {
        compareBuildOutput("tBPC") { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks")
                .withArguments("--all")
        }
    }
    
    @Test
    public void testUnpackAndSymlinks() {
    
        final String testName = "tUAS"
        
        // Make sure the 'symlinks' we expect this test to produce are not present before running test
        File testProjectDir = new File(getTestDir(), testName)
        String[] dependencyDirs = ["mylib", "anotherlib", "direct_dep_on_mylowerlevellib"]
        dependencyDirs.each { dirName ->
            File dependencyDir = new File(testProjectDir, dirName)
            println "Trying to delete folder/symlink ${dependencyDir}"
            dependencyDir.deleteDir()
        }

        // Now that the symlinks are gone, delete the corresponding parts of the unpack cache.  Specifically, anotherlib
        // will still be present there, even though the other two libraries will have been deleted via their symlinks
        // above.
        (new File(gradleUserHome, "/unpackCache/com.example-corp")).deleteDir()

        compareBuildOutput("tUAS") { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")                
        }
    }
    
}

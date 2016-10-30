package holygradle

import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

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
                (~/Total time:.*/) : "Total time: [snipped]",
                (~/Gradle user home:.*/) : "Gradle user home: [snipped]",
                (~/Holy Gradle init script .*/) : "Holy Gradle init script [snipped]",
                (~/Using SNAPSHOT version\..*/) : null,
                (~/ UP-TO-DATE$/) : "",
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

    /**
     * This tests that a warning is logged if the requested version of a plugin is not selected.
     */
    @Test
    public void testVersionOverrideDetection() {
        // Fake up a version "0" of devenv-plugin.
        File pluginsRepoOverrideDir = new File(pluginsRepoOverride.toString() - (pluginsRepoOverride.scheme + ':'))
        File pluginDir = new File(pluginsRepoOverrideDir, "holygradle/custom-gradle-core-plugin")
        String originalVersion = "0.0-hughgSNAPSHOT-0"
        File pluginOriginalVersionDir = new File(pluginDir, originalVersion)
        String overrideVersion = "0"
        File pluginOverrideVersionDir = new File(pluginDir, overrideVersion)
        FileHelper.ensureDeleteDirRecursive(pluginOverrideVersionDir)
        Project project = ProjectBuilder.builder().withProjectDir(new File(getTestDir(), "tVOD")).build()
        project.copy { CopySpec it ->
            it.from pluginOriginalVersionDir
            it.into pluginOverrideVersionDir
            it.rename "(.*)${originalVersion}(.*)", "\$1${overrideVersion}\$2"
            it.exclude "*.xml"
        }
        project.copy { CopySpec it ->
            it.from pluginOriginalVersionDir
            it.into pluginOverrideVersionDir
            it.rename "(.*)${originalVersion}(.*)", "\$1${overrideVersion}\$2"
            it.include "*.xml"
            it.filter { String line ->
                line.replaceAll(originalVersion, overrideVersion)
            }
        }

        // Now run the build.
        File projectDir = new File(getTestDir(), "tVOD")
        OutputStream outputStream = new ByteArrayOutputStream()
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks")
            launcher.setStandardOutput(outputStream)
        }
        List<String> outputLines = outputStream.toString().readLines()
        int messageIndex = outputLines.indexOf(
            "Buildscript for project ':other' requested holygradle:custom-gradle-core-plugin:0 but selected " +
            "holygradle:custom-gradle-core-plugin:0.0-hughgSNAPSHOT-0.  If this is not expected please check " +
            "plugin versions are consistent in all projects, including checking any resolutionStrategy.  " +
            "The reason for this selecton is: conflict resolution."
        )
        assertNotEquals("Override warning should appear in output", -1, messageIndex)
    }
}

package holygradle.packaging

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.junit.Assert.*

/**
 * Integration tests for {@link holygradle.packaging.PackageArtifactBuildScriptHandler}
 */
class PackageArtifactBuildScriptHandlerIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Rule
    public ErrorCollector collector = new ErrorCollector()

    /**
     * Test that build script generated using "addPinnedSourceDependency" produces the expected output.  (This is a
     * regression test for a bug introduced during GR#2081, whereby the "url@node" of the source dependency appeared as
     * that of the parent project.)
     */
    @Test
    public void testCreateBuildScriptWithPinnedSourceDependency() {
        // Create a repo with projectA in it.  This doesn't have to be a Gradle project.
        File projectADir = new File(getTestDir(), "projectA")
        if (projectADir.exists()) {
            assertTrue("Deleted pre-existing ${projectADir}", projectADir.deleteDir())
        }
        assertTrue("Created empty ${projectADir}", projectADir.mkdirs())
        File projectAInputDir = new File(getTestDir(), "projectAInput")
        projectAInputDir.listFiles().each { File file ->
            Files.copy(file.toPath(), new File(projectADir, file.name).toPath())
        }
        // We set up the repo by launching Gradle, because that means we can use the version of Mercurial which is
        // required by the plugins, which means we don't need to worry about the path to hg.exe.
        invokeGradle(projectADir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("setupRepo")
        }

        // ProjectB, references projectA as a sourceDependency, and has a meta-package which adds that as a pinned
        // sourceDependency.  We Run "packageEverything" for projectB, and regression-test the build.gradle of the
        // meta-package.  We delete projectB's "packages" folder first, otherwise the test will always see it as up to
        // date after the first run.
        File projectBDir = new File(getTestDir(), "projectB")
        File projectBPackagesDir = new File(projectBDir, "packages")
        if (projectBPackagesDir.exists()) {
            assertTrue("Deleted pre-existing ${projectBPackagesDir}", projectBPackagesDir.deleteDir())
        }

        // These two configurations should build normally.
        invokeGradle(projectBDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies", "packagePreBuiltArtifacts", "packagePinnedSource")
        }
        def configurations = [
            "preBuiltArtifacts",
            "pinnedSource",
        ]
        regressionTestBuildScriptForMultiplePackages("projectB", projectBPackagesDir, "tCBSWPSD", configurations)

        // This configuration should just fail to build.
        invokeGradle(projectBDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies", "packagePinnedSourceBad")
            launcher.expectFailure(
                "Looking for source dependencies [nonExistentSourceDep], failed to find [nonExistentSourceDep]"
            )
        }
    }

    @Test
    public void testCreateBuildScriptWithPackedDependency() {
        // ProjectC references a couple of packed dependencies, and when we run packageEverything it 
        // should generate a buildScript which only references only one of these - and the right one!

        File projectCDir = new File(getTestDir(), "projectC")
        File projectCPackagesDir = new File(projectCDir, "packages")
        if (projectCPackagesDir.exists()) {
            assertTrue("Deleted pre-existing ${projectCPackagesDir}", projectCPackagesDir.deleteDir())
        }

        invokeGradle(projectCDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies", "packageEverything")
        }

        ZipFile packageZip = new ZipFile(new File(projectCPackagesDir, "projectC-preBuiltArtifacts.zip"))
        ZipEntry packageBuildFile = packageZip.getEntry("preBuiltArtifacts/build.gradle")
        File testFile = regression.getTestFile("testCreateBuildScriptWithPackedDependency")
        testFile.text = packageZip.getInputStream(packageBuildFile).text.replaceAll(
            "gplugins.use \"(.*):.*\"",
            "gplugins.use \"\$1:dummy\""
        )
        regression.checkForRegression("testCreateBuildScriptWithPackedDependency")
    }

    /**
     * This test checks that the plugin correctly detects all the separate conditions which mean that it needs to write
     * a build script when packaging an artifact.
     */
    @Test
    public void testBuildScriptRequired() {
        File projectCDir = new File(getTestDir(), "buildScriptRequired")
        File projectCPackagesDir = new File(projectCDir, "packages")
        if (projectCPackagesDir.exists()) {
            assertTrue("Deleted pre-existing ${projectCPackagesDir}", projectCPackagesDir.deleteDir())
        }

        invokeGradle(projectCDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies", "packageEverything")
        }

        def configurations = [
            "explicitFile",
            "pinnedSource",
            "packedDependencyByName",
            "packedDependencyByCoordinate",
            "selfAsPackedDependencyByCoordinate",
            "packedDependencyFromSource",
            "ivyRepository",
            "withPublishPackages",
            "text",
            "withRepublishing",
        ]
        regressionTestBuildScriptForMultiplePackages("buildScriptRequired", projectCPackagesDir, "tBSR", configurations)
    }

    private ArrayList<String> regressionTestBuildScriptForMultiplePackages(
        String projectName,
        File packagesDir,
        String regressionFilePrefix,
        ArrayList<String> configurations
    ) {
        return configurations.each {
            try {
                ZipFile packageZip = new ZipFile(new File(packagesDir, "${projectName}-${it}.zip"))
                ZipEntry packageBuildFile = packageZip.getEntry("${it}/build.gradle")
                final String regressionFileName = "${regressionFilePrefix}_${it}"
                File testFile = regression.getTestFile(regressionFileName)
                if (packageBuildFile == null) {
                    testFile.text = ""
                } else {
                    testFile.text = packageZip.getInputStream(packageBuildFile).text
                }
                regression.replacePatterns(
                    regressionFileName, [
                    (~/gplugins.use "(.*):.*"/): "gplugins.use \"\$1:dummy\"",
                    (~/hg "unknown@[]+"]/)     : "hg \"unknown@[snipped]\""
                ]
                )
                regression.checkForRegression(regressionFileName)
            } catch (AssertionError e) {
                // This allows us to catch any assertion failures from a single configuration, carry on testing the
                // rest, and report all failures at the end.
                collector.addError(e)
            }
        }
    }
}

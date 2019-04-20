package holygradle.packaging

import holygradle.io.FileHelper
import holygradle.source_dependencies.RecursivelyFetchSourceTask
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import holygradle.testUtil.HgUtil
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull

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
        File testDir = getTestDir()
        // Create two repos with projectA in them.  This doesn't have to be a Gradle project.
        createRepoFromProjectAInput(testDir, "projectA")
        createRepoFromProjectAInput(testDir, "projectA2")

        // ProjectB, references projectA as a sourceDependency, and has a meta-package which adds that as a pinned
        // sourceDependency.  We run "packageEverything" for projectB, and regression-test the build.gradle of the
        // meta-package.  We delete projectB's "packages" folder first, otherwise the test will always see it as up to
        // date after the first run.
        File projectBDir = new File(getTestDir(), "projectB")
        File projectBPackagesDir = new File(projectBDir, "packages")
        FileHelper.ensureDeleteDirRecursive(projectBPackagesDir)
        FileHelper.ensureDeleteFile(new File(projectBDir, "settings.gradle"))
        FileHelper.ensureDeleteFile(new File(projectBDir, "settings-subprojects.txt"))

        // Invoke fAD once so that the settings file is created successfully, if it's not already there.
        invokeGradle(projectBDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
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

    protected void createRepoFromProjectAInput(File testDir, String projectDirName) {
        File projectADir = new File(testDir, projectDirName)
        FileHelper.ensureDeleteDirRecursive(projectADir)
        FileHelper.ensureMkdirs(projectADir)
        File projectAInputDir = new File(testDir, "projectAInput")
        projectAInputDir.listFiles().each { File file ->
            Files.copy(file.toPath(), new File(projectADir, file.name).toPath())
        }

        // Set up the project dir as a Mercurial repo.
        Project project = ProjectBuilder.builder().withProjectDir(projectADir).build()
        // Make the project dir into a repo, then add the extension.
        HgUtil.hgExec(project, "init")

        // Add a file.
        HgUtil.hgExec(project, "add", "build.gradle")
        // Set the commit message, user, and date, so that the hash will be the same every time.
        HgUtil.hgExec(
            project,
            "commit",
            "-m", "Initial test state.",
            "-u", "TestUser",
            "-d", "2000-01-01"
        )
    }

    @Test
    public void testCreateBuildScriptWithPackedDependency() {
        // ProjectC references a couple of packed dependencies, and when we run packageEverything it 
        // should generate a buildScript which only references only one of these - and the right one!

        File projectCDir = new File(getTestDir(), "projectC")
        File projectCPackagesDir = new File(projectCDir, "packages")
        FileHelper.ensureDeleteDirRecursive(projectCPackagesDir)

        // Invoke fAD once so that the settings file is created successfully, if it's not already there.
        invokeGradle(projectCDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        // These two configurations should build normally.
        invokeGradle(projectCDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies", "packagePreBuiltArtifacts", "packagePreBuiltArtifactsByCoord")
        }
        def configurations = [
            "preBuiltArtifacts",
            "preBuiltArtifactsByCoord",
        ]
        regressionTestBuildScriptForMultiplePackages("projectC", projectCPackagesDir, "tCBSWPD", configurations)

        // This configuration should just fail to build.
        invokeGradle(projectCDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies", "packagePreBuiltArtifactsBad")
            launcher.expectFailure(
                "Looking for packed dependencies [nonExistentPackedDep], failed to find [nonExistentPackedDep]"
            )
        }
    }

    /**
     * This test checks that the plugin correctly detects all the separate conditions which mean that it needs to write
     * a build script when packaging an artifact.
     */
    @Test
    public void testBuildScriptRequired() {
        File projectCDir = new File(getTestDir(), "buildScriptRequired")
        FileHelper.ensureDeleteFile(new File(projectCDir, "settings.gradle"))
        FileHelper.ensureDeleteFile(new File(projectCDir, "settings-subprojects.txt"))
        File projectCPackagesDir = new File(projectCDir, "packages")
        FileHelper.ensureDeleteDirRecursive(projectCPackagesDir)

        // Invoke fAD once so that the settings file is created successfully, if it's not already there.
        // Expect this to fail due to source dependencies.
        invokeGradle(projectCDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
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
            "noCreateDefaultSettingsFile",
        ]
        regressionTestBuildScriptForMultiplePackages("buildScriptRequired", projectCPackagesDir, "tBSR", configurations)
    }

    private List<String> regressionTestBuildScriptForMultiplePackages(
        String projectName,
        File packagesDir,
        String regressionFilePrefix,
        List<String> configurations
    ) {
        return configurations.each { String confName ->
            final File packageZipFile = new File(packagesDir, "${projectName}-${confName}.zip")
            ZipFile packageZip = new ZipFile(packageZipFile)
            try {
                ZipEntry packageBuildFile = packageZip.getEntry("${confName}/build.gradle")
                final String regressionFileName = "${regressionFilePrefix}_${confName}"
                File testFile = regression.getTestFile(regressionFileName)
                if (packageBuildFile == null) {
                    testFile.text = ""
                } else {
                    testFile.text = packageZip.getInputStream(packageBuildFile).text
                }
                regression.replacePatterns(
                    regressionFileName, [
                        (~/gplugins.use "(.*):.*"/): "gplugins.use \"\$1:dummy\"",
                        (~/hg "unknown@[0-9a-f]+"/): "hg \"unknown@[snipped]\""
                    ]
                )
                regression.checkForRegression(regressionFileName)
            } catch (AssertionError e) {
                // This allows us to catch any assertion failures from a single configuration, carry on testing the
                // rest, and report all failures at the end.
                collector.addError(e)
            }

            try {
                ZipEntry packageSettingsFile = packageZip.getEntry("${confName}/settings.gradle")
                if (confName == "noCreateDefaultSettingsFile") {
                    assertNull("Settings file should NOT exist in ${packageZipFile}", packageSettingsFile)
                } else {
                    assertNotNull("Settings file should exist in ${packageZipFile}", packageSettingsFile)
                }
            } catch (AssertionError e) {
                // This allows us to catch any assertion failures from a single configuration, carry on testing the
                // rest, and report all failures at the end.
                collector.addError(e)
            }
        }
    }
}

package holygradle.dependencies

import holygradle.Helper
import holygradle.io.Link
import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.RegressionFileHelper
import holygradle.test.WrapperBuildLauncher
import org.apache.commons.io.FileUtils
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class ReplaceWithSourceIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void twoLevels() {
        String projectName = "projectA"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "external-lib")

        if (projectDir.exists()) {
            Link.delete(frameworkDirectory)
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        File sourceFile1 = new File(frameworkDirectory, "a_source_file.txt")

        File sourceFile2 = new File(externalDirectory, "another_source_file.txt")

        assertTrue("Dependency 1 is correctly linked to source", sourceFile1.exists())
        assertTrue("Dependency 2 is correctly linked to source", sourceFile2.exists())
    }

    @Test
    public void oneLevelWithNestedSourceDependencies() {
        String projectName = "projectB"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File frameworkDirectory = new File(projectDir, "framework")
        File anotherDirectory = new File(projectDir, "another-lib")
        File externalDirectory = new File(projectDir, "external-lib")

        if (projectDir.exists()) {
            Link.delete(frameworkDirectory)
            Link.delete(anotherDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        File sourceFile1 = new File(frameworkDirectory, "a_source_file.txt")

        assertTrue("Dependency 1 is correctly linked to source", sourceFile1.exists())
        assertTrue("Dependency 2 is correctly linked to another-lib via source", anotherDirectory.exists())
        assertFalse("Dependency 2 is correctly not linked to external-lib via binary", externalDirectory.exists())
    }

    @Test
    public void oneLevelWithNestedSourceVersionConflict() {
        String projectName = "projectC"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(frameworkDirectory)
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                "Could not resolve all dependencies for configuration ':bar'.",
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
        }
    }

    @Test
    public void oneLevelWithNestedSourceVersionConflictMultipleConfigurations() {
        String projectName = "projectD"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "ext_11")
        File externalLibDirectory = new File(projectDir, "external-lib")

        if (projectDir.exists()) {
            Link.delete(frameworkDirectory)
            Link.delete(externalDirectory)
            Link.delete(externalLibDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }
    }

    /**
     * This tests that, if you have two source overrides, and one has a dependency on a different (non-overridden)
     * version of the other in some connected configurations, then we spot that conflict and fail the build.
     */
    @Test
    public void incompatibleTransitiveDependencies() {
        String projectName = "projectE"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File applicationDirectory = new File(projectDir, "application")
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(applicationDirectory)
            Link.delete(frameworkDirectory)
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        String expectedDummyVersion =
            Helper.convertPathToVersion(new File(projectDir, "../" + projectName + "source/ext-1.1").toString())
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                    """Caused by: org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException: A conflict was found between the following modules:""")

            // This error message has changed. It would be better to include the following lines in the expected error message:
            //"""\n - holygradle.test:external-lib:${expectedDummyVersion}""" +
            //"""\n - holygradle.test:external-lib:1.0""")
        }
    }

    /**
     * This tests that, if you have two source overrides, and each both have a dependency on a different
     * (non-overridden) version of the same module in some connected configurations, then we spot that conflict and fail
     * the build.
     */
    @Test
    public void incompatibleSourceOverrideTransitiveDependencies() {
        String projectName = "projectJ"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File applicationDirectory = new File(projectDir, "application")
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(applicationDirectory)
            Link.delete(frameworkDirectory)
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                    """Caused by: org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException: A conflict was found between the following modules:""")

            // This error message has changed. It would be better to include the following lines in the expected error message:
            //"""\n - holygradle.test:external-lib:1.1""" +
            //"""\n - holygradle.test:external-lib:1.0""")
        }
    }

    /**
     * This tests that, if you have a direct dependency on one (non-overridden) version of a module, and a source
     * override of another module which has a direct dependency on a different (non-overridden) version of that module,
     * then we spot that conflict and fail the build.
     */
    @Test
    public void incompatibleDirectVsSourceOverrideTransitiveDependencies() {
        String projectName = "projectK"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File applicationDirectory = new File(projectDir, "application")
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(applicationDirectory)
            Link.delete(frameworkDirectory)
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                    """Caused by: org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException: A conflict was found between the following modules:""")

                    // This error message has changed. It would be better to include the following lines in the expected error message:
                    //"""\n - holygradle.test:external-lib:1.1""" +
                    //"""\n - holygradle.test:external-lib:1.0""")
        }
    }

    @Test
    public void customIvyFileGenerator() {
        String projectName = "projectF"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File externalDirectory = new File(projectDir, "ext_11")

        File customFile = new File(externalDirectory, "custom")
        File sourceOverrideFile = new File(externalDirectory, "generateSourceOverrideDetails")
        File gradlewFile = new File(externalDirectory, "gradlew")

        if (projectDir.exists()) {
            customFile.delete()
            sourceOverrideFile.delete()
            gradlewFile.delete()

            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        assertTrue(customFile.exists())
        assertFalse(sourceOverrideFile.exists())
        assertFalse(gradlewFile.exists())
    }

    @Test
    public void gradlewBatIvyFileGeneratorFallback() {
        String projectName = "projectG"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File externalDirectory = new File(projectDir, "ext_11")
        File gradlewFile = new File(externalDirectory, "gradlew")

        if (projectDir.exists()) {
            gradlewFile.delete()

            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        assertTrue(gradlewFile.exists())
        assertTrue(gradlewFile.text.contains("generateDescriptorFileForIvyPublication"))
        assertTrue(gradlewFile.text.contains("summariseAllDependencies"))
    }

    @Test
    public void noIvyFileGeneration() {
        String projectName = "projectH"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                "No Ivy file generation available for 'ext_11'. Please ensure your source override contains " +
                "a generateSourceOverrideDetails.bat, or a compatible gradlew.bat, " +
                "or else provide a custom generation method in your build.gradle"
            )
        }
    }

    @Test
    public void failsWithNoUnpackToCache() {
        String projectName = "projectI"
        File templateDir = new File(getTestDir(), projectName + "In")
        File projectDir = new File(getTestDir(), projectName)
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(externalDirectory)
            externalDirectory.delete()
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles(projectName)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure("A source override can not be applied to a packed dependency with unpackToCache = false")
        }
    }

    @Test
    public void cantPublishWithSourceOverrides() {
        // Todo
    }

    @Test
    public void overrideIsNotAPackedDependency() {
        // Todo: Decide what to do in this case
    }

    private void copySourceFiles(String project) {
        File templateDir = new File(getTestDir(), "sourceIn")
        File sourceDir = new File(getTestDir(), project + "source")

        if (sourceDir.exists()) {
            FileHelper.ensureDeleteDirRecursive(sourceDir)
        }

        FileUtils.copyDirectory(templateDir, sourceDir)
    }
}

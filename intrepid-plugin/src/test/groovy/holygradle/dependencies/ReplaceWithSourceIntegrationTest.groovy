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
        File templateDir = new File(getTestDir(), "projectAIn")
        File projectDir = new File(getTestDir(), "projectA")
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "external-lib")

        if (projectDir.exists()) {
            Link.delete(frameworkDirectory)
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

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
        File templateDir = new File(getTestDir(), "projectBIn")
        File projectDir = new File(getTestDir(), "projectB")
        File frameworkDirectory = new File(projectDir, "framework")
        File anotherDirectory = new File(projectDir, "another-lib")
        File externalDirectory = new File(projectDir, "external-lib")

        if (projectDir.exists()) {
            Link.delete(frameworkDirectory)
            Link.delete(anotherDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

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
        File templateDir = new File(getTestDir(), "projectCIn")
        File projectDir = new File(getTestDir(), "projectC")
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(frameworkDirectory)
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

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
        File templateDir = new File(getTestDir(), "projectDIn")
        File projectDir = new File(getTestDir(), "projectD")
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
        copySourceFiles()

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
        File templateDir = new File(getTestDir(), "projectEIn")
        File projectDir = new File(getTestDir(), "projectE")
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
        copySourceFiles()

        String expectedDummyVersion = Helper.convertPathToVersion(new File(projectDir, "../source/ext-1.1").toString())
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
                    """FAILURE: Build failed with an exception.

* What went wrong:
Could not resolve all dependencies for configuration ':bar'.
> A conflict was found between the following modules:
   - holygradle.test:external-lib:${expectedDummyVersion}
   - holygradle.test:external-lib:1.1
"""
            ))
        }
    }

    /**
     * This tests that, if you have two source overrides, and each both have a dependency on a different
     * (non-overridden) version of the same module in some connected configurations, then we spot that conflict and fail
     * the build.
     */
    @Test
    public void incompatibleSourceOverrideTransitiveDependencies() {
        File templateDir = new File(getTestDir(), "projectJIn")
        File projectDir = new File(getTestDir(), "projectJ")
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
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
"""FAILURE: Build failed with an exception.

* What went wrong:
Could not resolve all dependencies for configuration ':bar'.
> A conflict was found between the following modules:
   - holygradle.test:external-lib:1.1
   - holygradle.test:external-lib:1.0
"""
            ))
        }
    }

    /**
     * This tests that, if you have a direct dependency on one (non-overridden) version of a module, and a source
     * override of another module which has a direct dependency on a different (non-overridden) version of that module,
     * then we spot that conflict and fail the build.
     */
    @Test
    public void incompatibleDirectVsSourceOverrideTransitiveDependencies() {
        File templateDir = new File(getTestDir(), "projectKIn")
        File projectDir = new File(getTestDir(), "projectK")
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
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
               """FAILURE: Build failed with an exception.

* What went wrong:
Could not resolve all dependencies for configuration ':bar'.
> A conflict was found between the following modules:
   - holygradle.test:external-lib:1.1
   - holygradle.test:external-lib:1.0
"""
            ))
        }
    }

    @Test
    public void customIvyFileGenerator() {
        File templateDir = new File(getTestDir(), "projectFIn")
        File projectDir = new File(getTestDir(), "projectF")
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
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        assertTrue(customFile.exists())
        assertFalse(sourceOverrideFile.exists())
        assertFalse(gradlewFile.exists())
    }

    @Test
    public void gradlewBatIvyFileGeneratorFallback() {
        File templateDir = new File(getTestDir(), "projectGIn")
        File projectDir = new File(getTestDir(), "projectG")
        File externalDirectory = new File(projectDir, "ext_11")
        File gradlewFile = new File(externalDirectory, "gradlew")

        if (projectDir.exists()) {
            gradlewFile.delete()

            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        assertTrue(gradlewFile.exists())
        assertTrue(gradlewFile.text.contains("generateIvyModuleDescriptor"))
        assertTrue(gradlewFile.text.contains("summariseAllDependencies"))
    }

    @Test
    public void noIvyFileGeneration() {
        File templateDir = new File(getTestDir(), "projectHIn")
        File projectDir = new File(getTestDir(), "projectH")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(externalDirectory)
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

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
        File templateDir = new File(getTestDir(), "projectIIn")
        File projectDir = new File(getTestDir(), "projectI")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Link.delete(externalDirectory)
            externalDirectory.delete()
            FileHelper.ensureDeleteDirRecursive(projectDir)
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

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

    private void copySourceFiles() {
        File templateDir = new File(getTestDir(), "sourceIn")
        File sourceDir = new File(getTestDir(), "source")

        if (sourceDir.exists()) {
            FileHelper.ensureDeleteDirRecursive(sourceDir)
        }

        FileUtils.copyDirectory(templateDir, sourceDir)
    }
}

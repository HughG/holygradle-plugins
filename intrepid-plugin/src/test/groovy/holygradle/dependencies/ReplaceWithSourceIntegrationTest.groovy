package holygradle.dependencies

import holygradle.custom_gradle.util.Symlink
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.apache.commons.io.FileUtils
import org.junit.Assert
import org.junit.Test

import java.nio.file.Files

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertTrue

class ReplaceWithSourceIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void twoLevels() {
        File templateDir = new File(getTestDir(), "projectAIn")
        File projectDir = new File(getTestDir(), "projectA")
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "external-lib")

        if (projectDir.exists()) {
            Symlink.delete(frameworkDirectory)
            Symlink.delete(externalDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
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
            Symlink.delete(frameworkDirectory)
            Symlink.delete(anotherDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
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
            Symlink.delete(frameworkDirectory)
            Symlink.delete(externalDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
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

        if (projectDir.exists()) {
            Symlink.delete(frameworkDirectory)
            Symlink.delete(externalDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                "Could not resolve all dependencies for configuration ':everything'.",
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
        }
    }

    @Test
    public void incompatibleTransitiveDependencies() {
        File templateDir = new File(getTestDir(), "projectEIn")
        File projectDir = new File(getTestDir(), "projectE")
        File applicationDirectory = new File(projectDir, "application")
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Symlink.delete(applicationDirectory)
            Symlink.delete(frameworkDirectory)
            Symlink.delete(externalDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                "Module 'holygradle.test:external-lib:D_Projects_holy-gradle-...ationTest_source_ext-1.1' does not match the dependency declared in source override 'application' (holygradle.test:external-lib:1.1). You may have to declare a matching source override in the source project."
            )
        }
    }

    @Test
    public void incompatibleSourceOverrideTransitiveDependencies() {
    }

    @Test
    public void generateFullDependenciesList() {
        // Todo: Probably put this somewhere else, probably just a unit test
    }

    @Test
    public void customIvyFileGenerator() {
        File templateDir = new File(getTestDir(), "projectFIn")
        File projectDir = new File(getTestDir(), "projectF")
        File externalDirectory = new File(projectDir, "ext_11")

        File customFile = new File(externalDirectory, "custom")
        File sourceOverrideFile = new File(externalDirectory, "generateSourceOverrideDetails")
        File gwFile = new File(externalDirectory, "gw")

        if (projectDir.exists()) {
            customFile.delete()
            sourceOverrideFile.delete()
            gwFile.delete()

            Symlink.delete(externalDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        assertTrue(customFile.exists())
        assertFalse(sourceOverrideFile.exists())
        assertFalse(gwFile.exists())
    }

    @Test
    public void gwBatIvyFileGeneratorFallback() {
        File templateDir = new File(getTestDir(), "projectGIn")
        File projectDir = new File(getTestDir(), "projectG")
        File externalDirectory = new File(projectDir, "ext_11")
        File gwFile = new File(externalDirectory, "gw")

        if (projectDir.exists()) {
            gwFile.delete()

            Symlink.delete(externalDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }

        assertTrue(gwFile.exists())
        assertTrue(gwFile.text.contains("generateIvyModuleDescriptor"))
        assertTrue(gwFile.text.contains("summariseAllDependencies"))
    }

    @Test
    public void noIvyFileGeneration() {
        File templateDir = new File(getTestDir(), "projectHIn")
        File projectDir = new File(getTestDir(), "projectH")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Symlink.delete(externalDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
        }

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure("No Ivy file generation available for 'ext_11'. Please ensure your source override contains a generateSourceOverrideDetails.bat, a compatible gw.bat or provide a custom generation method in your build.gradle")
        }
    }

    @Test
    public void failsWithNoUnpackToCache() {
        File templateDir = new File(getTestDir(), "projectIIn")
        File projectDir = new File(getTestDir(), "projectI")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Symlink.delete(externalDirectory)
            externalDirectory.delete()
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
        }

        println("%"*50)

        FileUtils.copyDirectory(templateDir, projectDir)
        copySourceFiles()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure("A source override can not be applied to a packed dependency with unpackToCache = false")
        }
    }

    private void copySourceFiles() {
        File templateDir = new File(getTestDir(), "sourceIn")
        File sourceDir = new File(getTestDir(), "source")

        if (sourceDir.exists()) {
            FileUtils.deleteDirectory(sourceDir)
        }

        FileUtils.copyDirectory(templateDir, sourceDir)
    }
}

package holygradle.dependencies

import holygradle.custom_gradle.util.Symlink
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.apache.commons.io.FileUtils
import org.junit.Assert
import org.junit.Test

import java.nio.file.Files

import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertTrue

class ReplaceWithSourceIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void twoLevels() {
        File templateDir = new File(getTestDir(), "projectAIn")
        File projectDir = new File(getTestDir(), "projectA")
        File frameworkDirectory = new File(projectDir, "framework")
        File externalDirectory = new File(projectDir, "ext_11")

        if (projectDir.exists()) {
            Symlink.delete(frameworkDirectory)
            Symlink.delete(externalDirectory)
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
        }

        FileUtils.copyDirectory(templateDir, projectDir)

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

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
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

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }
    }
}

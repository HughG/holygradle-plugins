package holygradle.unpacking

import holygradle.custom_gradle.util.Symlink
import holygradle.dependencies.PackedDependenciesSettingsHandler
import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class PackedDependenciesStateHandlerIntegrationTest extends AbstractHolyGradleIntegrationTest {
    /**
     * Tests that indirect dependencies will be fetched, even if they is reached only via configurations which have no
     * artifacts, and even if they are reached via different configurations.  (The latter check is because at one point
     * the code only fetched such dependencies via the first destination configuration encountered from a given origin
     * configuration.)
     */
    @Test
    public void useModulesViaEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useModulesViaEmptyConfigs")
        doUseModulesViaEmptyConfigs(
            new File(projectDir, "empty-config-lib"),
            new File(projectDir, "external-lib"),
            new File(projectDir, "another-lib"),
            projectDir
        )
    }

    /**
     * Tests that indirect dependencies will be fetched, even if they is reached only via configurations which have no
     * artifacts, and even if they are reached via different configurations.  (The latter check is because at one point
     * the code only fetched such dependencies via the first destination configuration encountered from a given origin
     * configuration.)  This version of the test checks backwards-compatibility mode 'useRelativePathFromIvyXml = true'.
     */
    @Test
    public void useModulesViaEmptyConfigsUsingRelativePathFromIvyXml() {
        Project project = ProjectBuilder.builder().build()
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).useRelativePathFromIvyXml = true

        final File projectDir = new File(getTestDir(), "useModulesViaEmptyConfigsURPFIX")
        doUseModulesViaEmptyConfigs(
            new File(projectDir, "empty-config-lib"),
            new File(projectDir, "extlib"),
            new File(projectDir, "anotherlib"),
            projectDir
        )
    }

    private void doUseModulesViaEmptyConfigs(
        File emptyConfigLibDir,
        File extLibDir,
        File anotherLibDir,
        File projectDir
    ) {
        [emptyConfigLibDir, extLibDir, anotherLibDir].each { FileHelper.ensureDeleteDirRecursive(it) }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("fAD")
        }

        [emptyConfigLibDir, extLibDir, anotherLibDir].each {
            assertTrue("File/folder for packed dep has been created at ${it}", it.exists())
            assertTrue("Symlink to packed dep has been created at ${it}", Symlink.isJunctionOrSymlink(it))
            // The output of "list()" doesn't include "./" and "../", so "> 0" tells us it's non-empty.
            assertTrue("Symlink target folder is not empty under ${it}", it.list().length > 0)
        }
    }

    /**
     * Test that, if you depend on a module only via configurations which have no artifacts, and those lead to other
     * module configurations which do have artifacts, then (a) no symlink is created for the empty-configs module; and
     * (b) symlinks are still created for the other modules.
     *
     * This is a (regression) test for a case discovered while developing GR #4125.
     */
    @Test
    public void useOnlyViaEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useOnlyViaEmptyConfigs")
        doUseOnlyViaEmptyConfigs(
            new File(projectDir, "empty-config-lib"),
            new File(projectDir, "external-lib"),
            new File(projectDir, "another-lib"),
            projectDir
        )
    }

    /**
     * Test that, if you depend on a module only via configurations which have no artifacts, and those lead to other
     * module configurations which do have artifacts, then (a) no symlink is created for the empty-configs module; and
     * (b) symlinks are still created for the other modules.
     *
     * This is a (regression) test for a case discovered while developing GR #4125.
     */
    @Test
    public void useOnlyViaEmptyConfigsUsingRelativePathFromIvyXml() {
        Project project = ProjectBuilder.builder().build()
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).useRelativePathFromIvyXml = true

        final File projectDir = new File(getTestDir(), "useOnlyViaEmptyConfigsURPFIX")
        doUseOnlyViaEmptyConfigs(
            new File(projectDir, "empty-config-lib"),
            new File(projectDir, "extlib"),
            new File(projectDir, "anotherlib"),
            projectDir
        )
    }

    private void doUseOnlyViaEmptyConfigs(
        File emptyConfigLibDir,
        File extLibDir,
        File anotherLibDir,
        File projectDir
    ) {
        final Collection<File> allPackedDepDirs = [emptyConfigLibDir, extLibDir, anotherLibDir]
        allPackedDepDirs.each { FileHelper.ensureDeleteDirRecursive(it) }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("fAD")
        }

        final Collection<File> symlinkPackedDepDirs = [extLibDir, anotherLibDir]
        allPackedDepDirs.each { File file ->
            final boolean expectSymlink = symlinkPackedDepDirs.contains(file)
            assertEquals(
                "Symlink to packed dep has been created at ${file}",
                expectSymlink,
                file.exists() && Symlink.isJunctionOrSymlink(file)
            )
            if (expectSymlink) {
                // The output of "list()" doesn't include "./" and "../", so "> 0" tells us it's non-empty.
                assertTrue("Symlink target folder is not empty under ${file}", file.list().length > 0)
            }
        }
    }

    /**
     * Test that, if you depend on conflicting versions of a module, the winning one is correctly unpacked.
     *
     * This is a (regression) test for a case discovered while developing GR #4071.
     */
    @Test
    public void useConflictingVersions() {
        final File projectDir = new File(getTestDir(), "useConflictingVersions")
        final File emptyConfigLibDir = new File(projectDir, "example-framework")
        final File extLibDir = new File(projectDir, "external-lib")
        final Collection<File> allPackedDepDirs = [emptyConfigLibDir, extLibDir]
        allPackedDepDirs.each { FileHelper.ensureDeleteDirRecursive(it) }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("fAD")
        }

        allPackedDepDirs.each { File file ->
            assertTrue(
                "Symlink to packed dep has been created at ${file}",
                file.exists() && Symlink.isJunctionOrSymlink(file)
            )
        }
        Path linkTarget = Files.readSymbolicLink(extLibDir.toPath())
        println "linkTarget = ${linkTarget}"
        assertThat(linkTarget.toString(), endsWith("\\external-lib-1.1"))
    }
}

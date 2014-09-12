package holygradle.unpacking

import holygradle.custom_gradle.util.Symlink
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
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
        final File emptyConfigLibDir = new File(projectDir, "empty-config-lib")
        final File extLibDir = new File(projectDir, "extlib")
        final File anotherLibDir = new File(projectDir, "anotherlib")
        [emptyConfigLibDir, extLibDir, anotherLibDir].each {
            if (it.exists()) {
                assertTrue("Removed existing packed dep symlink ${it}", it.delete())
            }
        }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("fAD")
        }

        [emptyConfigLibDir, extLibDir, anotherLibDir].each {
            assertTrue("File/folder for packed dep has been created at ${it}",it.exists())
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
    public void useOnlyEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useOnlyEmptyConfigs")
        final File emptyConfigLibDir = new File(projectDir, "empty-config-lib")
        final File extLibDir = new File(projectDir, "extlib")
        final File anotherLibDir = new File(projectDir, "anotherlib")
        final Collection<File> allPackedDepDirs = [emptyConfigLibDir, extLibDir, anotherLibDir]
        allPackedDepDirs.each {
            if (it.exists()) {
                assertTrue("Removed existing packed dep symlink ${it}", it.delete())
            }
        }

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
        allPackedDepDirs.each {
            if (it.exists()) {
                assertTrue("Removed existing packed dep symlink ${it}", it.delete())
            }
        }

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

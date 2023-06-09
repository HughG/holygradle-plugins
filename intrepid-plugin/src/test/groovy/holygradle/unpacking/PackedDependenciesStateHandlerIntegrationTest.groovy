package holygradle.unpacking

import holygradle.io.FileHelper
import holygradle.io.Link
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

import java.nio.file.Path

import static org.hamcrest.Matchers.endsWith
import static org.junit.Assert.*

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
        final File anotherLibDir = new File(projectDir, "another-lib")
        final File extLibDir = new File(projectDir, "external-lib")
        final File emptyConfigLibDir = new File(projectDir, "empty-config-lib")
        [emptyConfigLibDir, extLibDir, anotherLibDir].each { FileHelper.ensureDeleteDirRecursive(it) }
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("fAD")
        }
        [emptyConfigLibDir, extLibDir, anotherLibDir].each {
            assertTrue("File/folder for packed dep has been created at ${it}", it.exists())
            assertTrue("Link to packed dep has been created at ${it}", Link.isLink(it))
            // The output of "list()" doesn't include "./" and "../", so "> 0" tells us it's non-empty.
            assertTrue("Link target folder is not empty under ${it}", it.list().length > 0)
        }
    }

    /**
     * Test that, if you depend on a module only via configurations which have no artifacts, and those lead to other
     * module configurations which do have artifacts, then (a) no link is created for the empty-configs module; and
     * (b) links are still created for the other modules.
     *
     * This is a (regression) test for a case discovered while developing GR #4125.
     */
    @Test
    public void useOnlyViaEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useOnlyViaEmptyConfigs")
        final File anotherLibDir = new File(projectDir, "another-lib")
        final File extLibDir = new File(projectDir, "external-lib")
        final Collection<File> allPackedDepDirs = [new File(projectDir, "empty-config-lib"), extLibDir, anotherLibDir]
        allPackedDepDirs.each { FileHelper.ensureDeleteDirRecursive(it) }
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("fAD")
        }
        final Collection<File> linkPackedDepDirs = [extLibDir, anotherLibDir]
        allPackedDepDirs.each { File file ->
            final boolean expectLink = linkPackedDepDirs.contains(file)
            assertEquals(
                "Link to packed dep has been created at ${file}",
                expectLink,
                file.exists() && Link.isLink(file)
            )
            if (expectLink) {
                // The output of "list()" doesn't include "./" and "../", so "> 0" tells us it's non-empty.
                assertTrue("Link target folder is not empty under ${file}", file.list().length > 0)
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
                "Link to packed dep has been created at ${file}",
                file.exists() && Link.isLink(file)
            )
        }

        Path linkTarget = Link.getTarget(extLibDir).toPath()
        println "linkTarget = ${linkTarget}"
        assertThat(linkTarget.toString(), endsWith("\\external-lib-1.1"))
    }
}

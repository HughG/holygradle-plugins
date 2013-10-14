package holygradle.packaging

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.junit.Assert.*

/**
 * Integration tests for {@link holygradle.packaging.PackageArtifactBuildScriptHandler}
 */
class PackageArtifactBuildScriptHandlerIntegrationTest extends AbstractHolyGradleIntegrationTest {
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
        invokeGradle(projectBDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies", "packageEverything")
        }

        // We do the regression test by pulling the build file out of the ZIP and writing it to a file for comparison.
        // We remove the "<username>-" prefix from plugin version numbers, as it will be different for every user.
        File projectBPackageDir = new File(projectBDir, "packages")
        File testFile = regression.getTestFile("testCreateBuildScriptWithPinnedSourceDependency")
        String username = System.getProperty("user.name").toLowerCase()
        ZipFile packageZip = new ZipFile(new File(projectBPackageDir, "projectB-preBuiltArtifacts.zip"))
        ZipEntry packageBuildFile = packageZip.getEntry("preBuiltArtifacts/build.gradle")
        testFile.text = packageZip.getInputStream(packageBuildFile).text.replaceAll(
            "gplugins.use \"(.*):.*\"",
            "gplugins.use \"\$1:dummy\""
        )
        regression.checkForRegression("testCreateBuildScriptWithPinnedSourceDependency")
    }
}
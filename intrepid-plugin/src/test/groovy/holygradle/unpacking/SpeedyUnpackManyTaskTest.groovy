package holygradle.unpacking

import groovy.mock.interceptor.StubFor
import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test

/**
 * Unit test for {@link SpeedyUnpackManyTask}.
 */
class SpeedyUnpackManyTaskTest extends AbstractHolyGradleTest {
    /*
     * These tests use several Groovy testing mechanisms, and avoid using certain other ones.
     *
     * {@link groovy.mock.interceptor.StubFor} is used not as a proper assertion-verifying stub, but to override methods
     * in some Groovy classes, to provide simpler, controlled behaviour.
     * See http://docs.codehaus.org/display/GROOVY/Using+MockFor+and+StubFor
     *
     * As described at http://docs.codehaus.org/display/GROOVY/Developer+Testing+using+Closures+instead+of+Mocks,
     * makeDummyResolvedArtifact uses "map-of-closures coercion" to tersely provide an implementation of an existing
     * interface.
     *
     * Note that map coercion does *NOT* work if you want to extend/override a Groovy *class*.  For that you need
     * to use StubFor/MockFor.  That will work on a Java class, but only if the caller is Groovy.
     *
     * There's also overriding of methods via the meta-object protocol, e.g.,
     * {@code someInstance.metaClass.myMethod = { customCodeHere() } }.  But that doesn't work on Java classes at all:
     * it appears to work, but your patched-in method will never be called.
     */

    // These tests create stub implementations of classes using a map-of-closures.
    // See <http://docs.codehaus.org/display/GROOVY/Developer+Testing+using+Closures+instead+of+Mocks>.

    private static final String TEST_UNPACK_DIR = "testUnpackDir"

    /**
     * Check that repeatedly running a task to unpack multiple artifacts does nothing after the first run (assuming
     * that it worked the first time).
     *
     * This is a regression test for a defect introduced in 85e6c912ac4f (for GR #2281), and fixed in fdad1a225ee4 (for
     * GR #2600).  The defect was that, if there were multiple artifacts, each would be unpacked every time the task
     * ran, even if they'd already been unpacked.  This was because the "version info file" written inside the
     * SpeedyUnpackTask class was no longer being written to in append mode, so every artifact after the first overwrote
     * the knowledge that the previous ones had been unpacked.
     */
    @Test
    void testRepeatedUnpackOfMultipleArtifacts() {
        ////////////////////////////////////////////////////////////////////////////////////////
        // Test setup.

        Project project = ProjectBuilder.builder()
            .withName("testProject")
            .withProjectDir(testDir)
            .build()

        // Add dummy BuildScriptDependencies to the project so that the SevenZipHelper class will work.
        project.ext.buildScriptDependencies = new DummyBuildScriptDependencies(project)

        // Stub the SevenZipHelper so that it doesn't really call 7zip, it just sets a flag to say it was asked to.
        // (If I still had patience today, I would use Groovy's mocking properly instead of handling the flag myself,
        // but I don't.)
        boolean ranSevenZip
        StubFor stubForSZH = new StubFor(SevenZipHelper)
        stubForSZH.ignore("getDependencies") { [] }
        stubForSZH.ignore("unzip") { File zipFile, File targetDirectory -> ranSevenZip = true }
        SevenZipHelper dummySZH = (SevenZipHelper)stubForSZH.proxyInstance([project] as Object[])

        List<File> files = (1..3).collect { new File("file${it}") }

        // (Re)create the directory which SpeedyUnpackManyTask will use.
        File testUnpackDir = new File(testDir, TEST_UNPACK_DIR)
        if (testUnpackDir.exists()) {
            // Note: File#delete() doesn't delete non-empty directory, but Groovy deleteDir extension method does.
            Assert.assertTrue("Deleted unpack dir", testUnpackDir.deleteDir())
        }
        // Note: File#mkdir() doesn't make a directory if its ancestors don't exist, but File#mkdirs() does.
        Assert.assertTrue("Created unpack dir", testUnpackDir.mkdirs())

        ////////////////////////////////////////////////////////////////////////////////////////
        // Now for the actual test.

        ModuleVersionIdentifier id = new DefaultModuleVersionIdentifier("org", "something", "1.0")

        // Set up the first task to test.
        SpeedyUnpackManyTask task1 = (SpeedyUnpackManyTask)project.task([type: SpeedyUnpackManyTask], "speedyUnpack1")
        task1.initialize(dummySZH)
        task1.addEntry(id, new UnpackEntry(files, testUnpackDir, false, true))

        // Run the task's action once and check that the unzipping happens (i.e., that the task tries to run 7zip)
        ranSevenZip = false
        task1.execute()
        Assert.assertTrue("On first run, unzipping happened", ranSevenZip)

        // Set a second task to test.  (The first task will have internally recorded that it has already run, and will
        // do nothing if we try to execute it again.)
        SpeedyUnpackManyTask task2 = (SpeedyUnpackManyTask)project.task([type: SpeedyUnpackManyTask], "speedyUnpack2")
        task2.initialize(dummySZH)
        task2.addEntry(id, new UnpackEntry(files, testUnpackDir, false, true))

        // Now run it again and check that it does nothing.
        ranSevenZip = false
        task2.execute()
        Assert.assertFalse("On second run, no unzipping happened", ranSevenZip)

        ////////////////////////////////////////////////////////////////////////////////////////
        // Test cleanup.

        // This is intentionally not in a "finally", so that the files are left around for inspection if the test fails.
        if (testUnpackDir.exists()) {
            Assert.assertTrue("Deleted unpack dir", testUnpackDir.deleteDir())
        }
    }
}

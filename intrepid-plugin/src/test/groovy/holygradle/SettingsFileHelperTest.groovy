package holygradle

import holygradle.test.*
import org.junit.Test

import java.nio.file.Files

import static org.junit.Assert.*

class SettingsFileHelperTest extends AbstractHolyGradleTest {
    /**
     * Test for just writing a settings file, without there being one there before.
     */
    @Test
    public void testWrite1() {
        doTestWrite("testWrite1")
    }

    /**
     * Test for upgrading from before there was an upgrade mechanism.  Should overwrite the settings file and create
     * the subprojects file.
     */
    @Test
    public void testUpgradeFromV1() {
        // Copy in an example "V1" settings file.
        doTestWrite("testUpgradeFromV1", this.&copyInitialInput)
    }

    /**
     * Test for upgrading with the upgrade mechanism.  Should replace only part of the settings file and create the
     * subprojects file.  It's not necessary to add a new test every time the settings file format changes, only if the
     * upgrade mechanism of BEGIN/END markers changes.  It will be necessary to update the regression output, though.
     */
    @Test
    public void testUpgradeFromV2() {
        // Copy in an example "V2" settings file.
        doTestWrite("testUpgradeFromV2", this.&copyInitialInput)
    }

    /**
     * Test for appending the helper code when the pre-existing file has no line ending on its last line.  After append,
     * all the pre-existing lines should appear first, and be the same as before (ignoring line endings), and the begin
     * marker should appear at the start of the line.  If we re-run, no more blank lines should be inserted.
     */
    @Test
    public void testAppendWithNoEol() {
        // Copy in an example file whose last line has no EOL character(s).
        doTestWrite("testAppendWithNoEol", this.&copyInitialInput)
    }

    public copyInitialInput(File settingsFile) {
        File initialSettingsFile = new File(settingsFile.parent, settingsFile.name.replaceAll('\\.test', '.in'))
        Files.copy(initialSettingsFile.toPath(), settingsFile.toPath())
    }

    private void doTestWrite(String testName, Closure closure = null) {
        Collection<String> includes = ['..\\foo', 'bar', 'blah/blah']

        File settingsFile = regression.getTestFile(testName)
        File settingsSubprojectsFile = regression.getTestFile("${testName}-subprojects")
        [settingsFile, settingsSubprojectsFile].each { it.delete() }

        if (closure) {
            closure(settingsFile)
        }

        2.times {
            Collection<String> newIncludes = SettingsFileHelper.writeSettingsFile(
                settingsFile,
                settingsSubprojectsFile,
                includes
            )
            assertEquals(['../foo', 'bar', 'blah/blah'], newIncludes)
            regression.checkForRegression(testName)
            regression.checkForRegression("${testName}-subprojects")
        }
    }

    @Test
    public void testNoChange0() {
        doTestDetectChanges("testNoChange0", [], [])
    }

    @Test
    public void testNoChange2() {
        doTestDetectChanges("testNoChange2", ['../foo', 'bar'], ['../foo', 'bar'])
    }

    @Test
    public void testChange0To1() {
        doTestDetectChanges("testChange0To1", [], ['../foo'])
    }

    @Test
    public void testChange1To2() {
        doTestDetectChanges("testChange1To2", ['../foo'], ['../foo', 'bar'])
    }

    @Test
    public void testChange2To1() {
        doTestDetectChanges("testChange2To1", ['../foo', 'bar'], ['bar'])
    }

    @Test
    public void testChange1To0() {
        doTestDetectChanges("testChange1To0", ['bar'], [])
    }

    @Test
    public void testChange2To1Different() {
        doTestDetectChanges("testChange2To1Different", ['../foo', 'bar'], ['bar', 'blah/blah'])
    }

    private void doTestDetectChanges(String testName, Collection<String> paths1, Collection<String> paths2) {
        File settingsFile = regression.getTestFile(testName)
        File settingsSubprojectsFile = SettingsFileHelper.getSettingsSubprojectsFile(settingsFile)
        [settingsFile, settingsSubprojectsFile].each {
            it.delete()
//            println "Deleted ${it}"
        }

        boolean changed1 = SettingsFileHelper.writeSettingsFileAndDetectChange(settingsFile, paths1)
        assertEquals("First paths collection change was detected correctly: ${paths1}", !(paths1.empty), changed1)
        boolean changed2 = SettingsFileHelper.writeSettingsFileAndDetectChange(settingsFile, paths2)
        assertEquals("Second paths collection change was detected correctly: ${paths1} -> ${paths2}", paths1 != paths2, changed2)
    }
}
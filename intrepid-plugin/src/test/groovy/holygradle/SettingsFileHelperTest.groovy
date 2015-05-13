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
        doTestWrite("testUpgradeFromV1") { File settingsFile ->
            // Copy in an example "V1" settings file.
            File initialSettingsFile = new File(settingsFile.parent, settingsFile.name.replaceAll('\\.test', '.in'))
            Files.copy(initialSettingsFile.toPath(), settingsFile.toPath())
        }
    }

    /**
     * Test for upgrading with the upgrade mechanism.  Should replace only part of the settings file and create the
     * subprojects file.  It's not necessary to add a new test every time the settings file format changes, only if the
     * upgrade mechanism of BEGIN/END markers changes.  It will be necessary to update the regression output, though.
     */
    @Test
    public void testUpgradeFromV2() {
        doTestWrite("testUpgradeFromV2") { File settingsFile ->
            // Copy in an example "V2" settings file.
            File initialSettingsFile = new File(settingsFile.parent, settingsFile.name.replaceAll('\\.test', '.in'))
            Files.copy(initialSettingsFile.toPath(), settingsFile.toPath())
        }
    }

    private void doTestWrite(String testName, Closure closure = null) {
        Collection<String> includes = ['..\\foo', 'bar', 'blah/blah']

        File settingsFile = regression.getTestFile(testName)
        File settingsSubprojectsFile = regression.getTestFile("${testName}-subprojects")
        [settingsFile, settingsSubprojectsFile].each { it.delete() }

        if (closure) {
            closure(settingsFile)
        }

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
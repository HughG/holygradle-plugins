package holygradle

import holygradle.test.*
import org.junit.Test

import java.nio.file.Files

import static org.junit.Assert.*

class SettingsFileHelperTest extends AbstractHolyGradleTest {
    @Test
    public void testWrite1() {
        Collection<String> includes = ['..\\foo', 'bar', 'blah/blah']

        File settingsFile = regression.getTestFile("testWrite1")
        File settingsSubprojectsFile = regression.getTestFile("testWrite1-subprojects")
        [settingsFile, settingsSubprojectsFile].each { it.delete() }

        Collection<String> newIncludes = SettingsFileHelper.writeSettingsFile(
            settingsFile,
            settingsSubprojectsFile,
            includes
        )
        assertEquals(['../foo', 'bar', 'blah/blah'], newIncludes)
        regression.checkForRegression("testWrite1")
        regression.checkForRegression("testWrite1-subprojects")
    }
}
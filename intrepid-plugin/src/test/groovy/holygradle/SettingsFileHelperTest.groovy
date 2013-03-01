package holygradle

import org.junit.Test
import static org.junit.Assert.*

class SettingsFileHelperTest extends TestBase {
    @Test
    public void testWrite1() {
        def includes = ['..\\foo', 'bar', '../blah/blah']
        String newIncludes = SettingsFileHelper.writeSettingsFile(regression.getTestFile("testWrite1"), includes)
        assertEquals(newIncludes, "include '..\\\\foo', 'bar', '../blah/blah'")
        regression.checkForRegression("testWrite1")
    }
}
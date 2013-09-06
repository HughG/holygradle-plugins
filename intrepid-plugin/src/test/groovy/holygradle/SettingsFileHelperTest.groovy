package holygradle

import holygradle.test.*
import org.junit.Test
import static org.junit.Assert.*

class SettingsFileHelperTest extends AbstractHolyGradleTest {
    @Test
    public void testWrite1() {
        Collection<String> includes = ['..\\foo', 'bar', '../blah/blah']
        String newIncludes = SettingsFileHelper.writeSettingsFile(regression.getTestFile("testWrite1"), includes)
        assertEquals(newIncludes, "include '..\\\\foo', 'bar', '../blah/blah'")
        regression.checkForRegression("testWrite1")
    }
}
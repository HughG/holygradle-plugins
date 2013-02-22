package holygradle

import org.junit.Test
import java.io.File
import static org.junit.Assert.*

class RegressionFileHelper {
    private TestBase testBase
    
    public RegressionFileHelper(TestBase testBase) {
        this.testBase = testBase
    }
    
    private String getBasePath(String testName) {
        def className = testBase.getClass().getName().replace(".", "/")
        return "src/test/groovy/${className}_${testName}"
    }
    
    public File getOkFile(String testName) {
        return new File(getBasePath(testName) + ".ok.txt")
    }
    
    public File getTestFile(String testName) {
        return new File(getBasePath(testName) + ".test.txt")
    }
    
    public void checkForRegression(String testName) {
        File okFile = getOkFile(testName)
        File testFile = getTestFile(testName)
        if (!okFile.exists()) {
            fail("Ok file ${okFile.path} does not exist.")
        }
        if (!testFile.exists()) {
            fail("Regression output file ${testFile.path} does not exist.")
        }
        assertEquals(okFile.text, testFile.text)
    }
}
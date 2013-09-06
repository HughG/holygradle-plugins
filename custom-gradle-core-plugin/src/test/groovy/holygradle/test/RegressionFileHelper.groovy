package holygradle.test

import static org.junit.Assert.*

class RegressionFileHelper {
    private AbstractHolyGradleTest testBase
    
    public RegressionFileHelper(AbstractHolyGradleTest testBase) {
        this.testBase = testBase
    }
    
    private File getBaseDir(String testName) {
        return new File(testBase.getTestDir().path + "_${testName}")
    }
    
    public File getOkFile(String testName) {
        return new File(getBaseDir(testName).path + ".ok.txt")
    }
    
    public File getTestFile(String testName) {
        return new File(getBaseDir(testName).path + ".test.txt")
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
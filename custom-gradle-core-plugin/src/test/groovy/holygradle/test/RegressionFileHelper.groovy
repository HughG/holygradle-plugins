package holygradle.test

import java.util.regex.Pattern

import static org.junit.Assert.*

class RegressionFileHelper {
    private AbstractHolyGradleTest testBase
    
    public RegressionFileHelper(AbstractHolyGradleTest testBase) {
        this.testBase = testBase
    }

    /**
     * Given a file and a map of "regex -> string", iterates over each line in the file replacing any matches of each
     * regex with the corresponding string, and then writes the result back to the file.  This is to remove variable
     * parts of regression output before calling {@link #checkForRegression(java.lang.String)}.
     *
     * @param testFile The file in which to replace patterns.
     * @param patterns The map of "regex -> string".
     */
    public void replacePatterns(String testName, Map<Pattern, String> patterns) {
        final File testFile = getTestFile(testName)
        holygradle.test.AbstractHolyGradleTest.useCloseable(new StringWriter()) { StringWriter s ->
            holygradle.test.AbstractHolyGradleTest.useCloseable(new PrintWriter(s)) { PrintWriter p ->
                testFile.eachLine { String line ->
                    patterns.each { Pattern pattern, String replacement ->
                        line = line.replaceAll(pattern, replacement)
                    }
                    p.println(line)
                }
            }

            testFile.write(s.toString())
        }
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
package holygradle.test

import groovy.io.PlatformLineWriter

import java.util.regex.Pattern

import static org.junit.Assert.*

class RegressionFileHelper {
    private AbstractHolyGradleTest testBase
    
    public RegressionFileHelper(AbstractHolyGradleTest testBase) {
        this.testBase = testBase
    }

    /**
     * Given a file and a map of "regex -> string", iterates over each line in the file replacing any matches of each
     * regex with the corresponding string, and then writes the result back to the file.  If the string is null, then
     * a match indicates that the line should be deleted altogether.  This is to remove variable parts of regression
     * output before calling {@link #checkForRegression(java.lang.String)}.
     *
     * @param testFile The file in which to replace patterns.
     * @param patterns The map of "regex -> string".
     */
    public void replacePatterns(String testName, Map<Pattern, String> patterns) {
        final File testFile = getTestFile(testName)
        AbstractHolyGradleTest.useCloseable(new StringWriter()) { StringWriter s ->
            AbstractHolyGradleTest.useCloseable(new PrintWriter(s)) { PrintWriter p ->
                testFile.eachLine { String line ->
                    boolean deleteLine = false

                    for (Map.Entry<Pattern, String> entry in patterns) {
                        Pattern pattern = entry.key
                        String replacement = entry.value
                        if (replacement == null) {
                            if (line.matches(pattern)) {
                                deleteLine = true
                                break
                            }
                        } else {
                            line = line.replaceAll(pattern, replacement)
                        }
                    }

                    if (!deleteLine) {
                        p.println(line)
                    }
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
        checkForRegression(testName, false)
    }

    public void checkForRegression(String testName, boolean stripEmptyLines) {
        File okFile = getOkFile(testName)
        File testFile = getTestFile(testName)
        if (!okFile.exists()) {
            fail("Ok file ${okFile.path} does not exist.")
        }
        if (!testFile.exists()) {
            fail("Regression output file ${testFile.path} does not exist.")
        }
        // This is annoying. The mechanism for replacing patterns doesn't allow for replacing multiple lines. So when
        // we have a situation where a line occurs in test output in some circumstances and does not affect the test
        // result, we can replace the line in question with null but then the test fails because of the empty line after.
        // This stripEmptyLines mechanism allows all empty lines to be removed from the file in such a situation.
        String okText = (stripEmptyLines) ? stripEmptyLinesFromText(okFile) : okFile.text
        String fileText = (stripEmptyLines) ? stripEmptyLinesFromText(testFile) : testFile.text
        assertEquals("Regression file check for ${testFile.name}", okText, fileText)
    }

    public String stripEmptyLinesFromText(File file) {
        StringBuffer sb = new StringBuffer()
        file.eachLine { String line ->
            if (!line.equals("")) {
                sb.append(line + "\n")
            }
        }
        String linesRemoved = sb.toString()
        return linesRemoved.trim()
    }

    public static String toStringWithPlatformLineBreaks(String lines) {
        StringWriter s = new StringWriter()
        AbstractHolyGradleTest.useCloseable(new PlatformLineWriter(s)) { Writer plw ->
            plw.withPrintWriter { PrintWriter pw ->
                lines.eachLine { String line ->
                    pw.println(line)
                }
            }
        }
        return s.toString()
    }
}
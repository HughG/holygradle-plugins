package holygradle.devenv

import org.junit.Test
import static org.junit.Assert.*

import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutput.Style

class ErrorHighlightingOutputStreamTest {
    private static void verifyStyle(Writer writer, List<String> recording, String txt, StyledTextOutput.Style style) {
        writer.write(txt)
        writer.flush()
        assertTrue(recording[0] == "withStyle: $style")
        assertTrue(recording[1] == "println: $txt")
        recording.clear()
    }
    
    @Test
    public void testColouredOutput() {
        List<String> recording = new LinkedList<String>()
        def mockOutput = []
        mockOutput = [
            withStyle: { style -> recording.add("withStyle: ${style}"); mockOutput as StyledTextOutput },
            println: { text -> recording.add("println: ${text}"); mockOutput as StyledTextOutput }
        ]
        
        DevEnvHandler handler = new DevEnvHandler(null, null)
        OutputStream outputStream = new ErrorHighlightingOutputStream(
            "Test", mockOutput as StyledTextOutput, handler.getWarningRegexes(), handler.getErrorRegexes()
        )
        Writer writer = new OutputStreamWriter(outputStream)
        
        verifyStyle(writer, recording, "5>  BlahBlah.cpp", Style.Normal)
                
        verifyStyle(writer, recording, "1>..\\foo.cpp(46): warning C2065: 'test_' : undeclared identifier", Style.Info)
        
        verifyStyle(writer, recording, "1>..\\bar.cpp(46): error C2065: 'test_' : undeclared identifier", Style.Failure)
        
        verifyStyle(writer, recording, "1>..\\bar2.cpp(46): fatal error C2065: 'test_' : undeclared identifier", Style.Failure)
        
        verifyStyle(writer, recording, "1>D:\\blah\\foo.dll: warning LNK4088: due to /FORCE option; image may not run", Style.Info)
        
        verifyStyle(writer, recording, "12>Build FAILED", Style.Failure)
        
        writer.write("3>  Foo.cpp\n12>Build FAILED")
        writer.flush()
        assertTrue(recording[0] == "withStyle: Normal")
        assertTrue(recording[1] == "println: 3>  Foo.cpp")
        assertTrue(recording[2] == "withStyle: Failure")
        assertTrue(recording[3] == "println: 12>Build FAILED")
        recording.clear()
    }
}
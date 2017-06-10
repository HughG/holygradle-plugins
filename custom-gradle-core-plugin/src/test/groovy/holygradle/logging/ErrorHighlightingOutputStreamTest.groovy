package holygradle.logging

import holygradle.logging.StyledTextOutput.Style
import org.junit.Test

import static org.junit.Assert.assertEquals

class ErrorHighlightingOutputStreamTest {
    private class SpyStyledTextOutput implements StyledTextOutput {
        public final List<String> recording = new LinkedList<String>()

        @Override
        void println(String str) {
            recording.add("println: ${str}".toString())
        }

        @Override
        void println() {
            recording.add("println")
        }

        @Override
        void withStyle(Style style, Closure action) {
            recording.add("withStyle: ${style}".toString())
            action(this)
        }
    }

    private static void verifyStyle(Writer writer, List<String> recording, String txt, Style style) {
        writer.write(txt)
        writer.flush()
        assertEquals("withStyle: $style".toString(), recording[0])
        assertEquals("println: $txt".toString(), recording[1])
        recording.clear()
    }
    
    @Test
    public void testColouredOutput() {
        def spy = new SpyStyledTextOutput()
        List<String> warningRegexes = [/.* warning \w+\d{2,5}:.*/]
        List<String> errorRegexes = [/\d+>Build FAILED/, /.* [error|fatal error]+ \w+\d{2,5}:.*/]
        OutputStream outputStream = new ErrorHighlightingOutputStream(
            "Test", spy, warningRegexes, errorRegexes
        )
        Writer writer = new OutputStreamWriter(outputStream)
        
        verifyStyle(writer, spy.recording, "5>  BlahBlah.cpp", Style.Normal)
                
        verifyStyle(writer, spy.recording, "1>..\\foo.cpp(46): warning C2065: 'test_' : undeclared identifier", Style.Info)
        
        verifyStyle(writer, spy.recording, "1>..\\bar.cpp(46): error C2065: 'test_' : undeclared identifier", Style.Failure)
        
        verifyStyle(writer, spy.recording, "1>..\\bar2.cpp(46): fatal error C2065: 'test_' : undeclared identifier", Style.Failure)
        
        verifyStyle(writer, spy.recording, "1>D:\\blah\\foo.dll: warning LNK4088: due to /FORCE option; image may not run", Style.Info)
        
        verifyStyle(writer, spy.recording, "12>Build FAILED", Style.Failure)

        writer.write("3>  Foo.cpp\n12>Build FAILED")
        writer.flush()
        assertEquals("withStyle: ${Style.Normal}".toString(), spy.recording[0])
        assertEquals("println: 3>  Foo.cpp".toString(), spy.recording[1])
        assertEquals("withStyle: ${Style.Failure}".toString(), spy.recording[2])
        assertEquals("println: 12>Build FAILED".toString(), spy.recording[3])
        spy.recording.clear()
    }
}
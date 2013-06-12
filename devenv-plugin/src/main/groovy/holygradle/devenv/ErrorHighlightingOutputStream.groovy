package holygradle.devenv

import org.gradle.logging.StyledTextOutput

class ErrorHighlightingOutputStream extends ByteArrayOutputStream {
    private ByteArrayOutputStream fullStream
    private String projectName
    private StyledTextOutput output
    private List<String> errors = []
    private List<String> warnings = []
    private List<String> warningRegexes
    private List<String> errorRegexes
    
    ErrorHighlightingOutputStream(String projectName, StyledTextOutput output, def warningRegexes, def errorRegexes) {
        fullStream = new ByteArrayOutputStream()
        this.projectName = projectName
        this.output = output
        this.warningRegexes = warningRegexes
        this.errorRegexes = errorRegexes
        
        /*this.output.withStyle(StyledTextOutput.Style.Error).println("Error")
        this.output.withStyle(StyledTextOutput.Style.Description).println("Description")
        this.output.withStyle(StyledTextOutput.Style.Failure).println("Failure")
        this.output.withStyle(StyledTextOutput.Style.Success).println("Success")
        this.output.withStyle(StyledTextOutput.Style.Info).println("Info")
        this.output.withStyle(StyledTextOutput.Style.Identifier).println("Identifier")
        this.output.withStyle(StyledTextOutput.Style.Header).println("Header")
        this.output.withStyle(StyledTextOutput.Style.UserInput).println("UserInput")
        this.output.withStyle(StyledTextOutput.Style.ProgressStatus).println("ProgressStatus")*/
    }
    
    @Override
    public synchronized void write(byte[] b, int off, int len) {
        super.write(b, off, len)
        fullStream.write(b, off, len)
        
        toString().eachLine {
            StyledTextOutput.Style style = StyledTextOutput.Style.Normal
            
            for (regex in warningRegexes) {
                if (it ==~ regex) {
                    style = StyledTextOutput.Style.Info
                    warnings.add(it)
                    break
                }
            }
            
            for (regex in errorRegexes) {
                if (it ==~ regex) {
                    style = StyledTextOutput.Style.Failure
                    errors.add(it)
                    break
                }
            }
            
            output.withStyle(style).println(it)
        }
        
        reset() 
    }

    public void summarise() {
        output.println()
        if (errors.size() > 0) {
            summarise(errors, "errors", StyledTextOutput.Style.Failure)
        }
        if (warnings.size() > 0) {
            summarise(warnings, "warnings", StyledTextOutput.Style.Info)
        }
    }

    private void summarise(List<String> messages, String type, StyledTextOutput.Style style) {
        final int LINE_LENGTH = 75
        String msg = " ${projectName}: ${messages.size()} ${type} "
        int firstDashes = (int) (LINE_LENGTH - msg.length()) / 2
        int secondDashes = (LINE_LENGTH - msg.length()) - firstDashes
        StyledTextOutput o = output.withStyle(style)
        o.println("=" * firstDashes + msg + "=" * secondDashes)
        messages.each {
            o.println(it)
        }
        o.println("=" * LINE_LENGTH)
        output.println()
    }

    public String getFullStreamString() {
        fullStream.toString()
    }
}

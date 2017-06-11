package holygradle.logging

import java.util.regex.Pattern

class ErrorHighlightingOutputStream extends ByteArrayOutputStream {
    private String projectName
    private StyledTextOutput output
    private Collection<String> errors = []
    private Collection<String> warnings = []
    private Iterable<Pattern> warningRegexes
    private Iterable<Pattern> errorRegexes
    
    ErrorHighlightingOutputStream(
            String projectName,
            StyledTextOutput output,
            Iterable<Pattern> warningRegexes,
            Iterable<Pattern> errorRegexes
    ) {
        this.projectName = projectName
        this.output = output
        this.warningRegexes = warningRegexes
        this.errorRegexes = errorRegexes
    }
    
    @Override
    public synchronized void write(byte[] b, int off, int len) {
        super.write(b, off, len)

        toString().eachLine { line ->
            StyledTextOutput.Style style = StyledTextOutput.Style.Normal
            
            for (regex in warningRegexes) {
                if (regex.matcher(line).matches()) {
                    style = StyledTextOutput.Style.Info
                    warnings.add(line)
                    break
                }
            }
            
            for (regex in errorRegexes) {
                if (regex.matcher(line).matches()) {
                    style = StyledTextOutput.Style.Failure
                    errors.add(line)
                    break
                }
            }
            
            output.withStyle(style) { it.println(line) }
        }
        
        reset() 
    }

    public void summarise() {
        output.println()
        if (errors.size() > 0) {
            summariseMessages(errors, "errors", StyledTextOutput.Style.Failure)
        }
        if (warnings.size() > 0) {
            summariseMessages(warnings, "warnings", StyledTextOutput.Style.Info)
        }
    }

    private void summariseMessages(Collection<String> messages, String type, StyledTextOutput.Style style) {
        final int LINE_LENGTH = 75
        String msg = " ${projectName}: ${messages.size()} ${type} "
        int firstDashes = (int) (LINE_LENGTH - msg.length()) / 2
        int secondDashes = (LINE_LENGTH - msg.length()) - firstDashes
        output.withStyle(style) { o ->
            o.println("=" * firstDashes + msg + "=" * secondDashes)
            messages.each {
                o.println(it)
            }
            o.println("=" * LINE_LENGTH)
        }
        output.println()
    }
}

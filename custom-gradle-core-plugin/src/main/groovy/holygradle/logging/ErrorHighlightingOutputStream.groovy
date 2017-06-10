package holygradle.logging

class ErrorHighlightingOutputStream extends ByteArrayOutputStream {
    private String projectName
    private StyledTextOutput output
    private Collection<String> errors = []
    private Collection<String> warnings = []
    private Collection<String> warningRegexes
    private Collection<String> errorRegexes
    
    ErrorHighlightingOutputStream(
            String projectName,
            StyledTextOutput output,
            Collection<String> warningRegexes,
            Collection<String> errorRegexes
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
                if (line ==~ regex) {
                    style = StyledTextOutput.Style.Info
                    warnings.add(line)
                    break
                }
            }
            
            for (regex in errorRegexes) {
                if (line ==~ regex) {
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

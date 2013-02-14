package holygradle.devenv

import org.gradle.*
import org.gradle.api.*
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory
import org.gradle.logging.StyledTextOutput.Style

class ErrorHighlightingOutputStream extends ByteArrayOutputStream {
    private ByteArrayOutputStream fullStream
    private String projectName
    private StyledTextOutput output
    private def errors = []
    private def warnings = []
    private def warningRegexes
    private def errorRegexes
    
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
    public void write(byte[] b, int off, int len) {
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
        def lineLength = 75
        if (errors.size() > 0) {
            output.println()
            def msg = " ${projectName}: ${errors.size()} errors "
            def firstDashes = (int)(lineLength - msg.length())/2
            def secondDashes = (lineLength - msg.length()) - firstDashes
            def o = output.withStyle(StyledTextOutput.Style.Failure)
            o.println("="*firstDashes + msg + "="*secondDashes)
            errors.each {
                o.println(it)
            }
            o.println("="*lineLength)
            output.println()
        }
        if (warnings.size() > 0) {
            def msg = " ${projectName}: ${warnings.size()} warnings "
            def firstDashes = (int)(lineLength - msg.length())/2
            def secondDashes = (lineLength - msg.length()) - firstDashes
            def o = output.withStyle(StyledTextOutput.Style.Info)
            o.println("="*firstDashes + msg + "="*secondDashes)
            warnings.each {
                o.println(it)
            }
            o.println("="*lineLength)
            output.println()
        }        
    }
    
    public String getFullStreamString() {
        fullStream.toString()
    }
}

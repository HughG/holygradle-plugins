package holygradle.logging

import static holygradle.logging.StyledTextOutput.Style

// TODO 2017-06-10 HughG: Implement colour output
class DefaultStyledTextOutput implements StyledTextOutput {
    private PrintStream output
    private Style style = Style.Normal

    DefaultStyledTextOutput(PrintStream output) {
        this.output = output
    }

    @Override
    public void println(String str) {
        output.print(style.toString())
        output.print(' ')
        output.println(str)
    }

    @Override
    public void println() {
        output.println()
    }

    @Override
    public void withStyle(Style style, Closure action) {
        Style savedStyle = this.style
        this.style = style
        try {
            action(this)
        } finally {
            this.style = savedStyle
        }
    }
}
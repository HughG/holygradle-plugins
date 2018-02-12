package holygradle.logging

import holygradle.logging.StyledTextOutput.Style
import java.io.PrintStream

// TODO 2017-06-10 HughG: Implement colour output
class DefaultStyledTextOutput(private val output: PrintStream) : StyledTextOutput {
    private var style = Style.Normal

    override fun println(str: String) {
        output.print(style.toString())
        output.print(' ')
        output.println(str)
    }

    override fun println() {
        output.println()
    }

    override fun withStyle(style: Style, action: StyledTextOutput.() -> Unit) {
        val savedStyle = this.style
        this.style = style
        try {
            action(this)
        } finally {
            this.style = savedStyle
        }
    }
}
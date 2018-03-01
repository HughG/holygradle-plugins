package holygradle.logging

import holygradle.gradle.api.invoke
import holygradle.logging.StyledTextOutput.Style
import org.gradle.api.Action
import java.io.PrintStream

// TODO 2017-06-10 HughG: Implement colour output
class DefaultStyledTextOutput(private val output: PrintStream) : AbstractStyledTextOutput() {
    private var style = Style.Normal

    override fun println(str: String) {
        output.print(style.toString())
        output.print(' ')
        output.println(str)
    }

    override fun println() {
        output.println()
    }

    override fun withStyle(style: Style, action: Action<StyledTextOutput>) {
        val savedStyle = this.style
        this.style = style
        try {
            action(this)
        } finally {
            this.style = savedStyle
        }
    }
}
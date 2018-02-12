package holygradle.logging

import holygradle.logging.StyledTextOutput.Style
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

class ErrorHighlightingOutputStream(
    private val projectName: String,
    private val output: StyledTextOutput,
    private val warningRegexes: Iterable<Pattern>,
    private val errorRegexes: Iterable<Pattern>
) : ByteArrayOutputStream() {
    private val errors = mutableListOf<String>()
    private val warnings = mutableListOf<String>()

    override fun write(b: ByteArray?, off: Int, len: Int) {
        synchronized(this) {

        super.write(b, off, len)
            toString().lines().forEach { line ->
                var style = Style.Normal

                for (regex in warningRegexes) {
                    if (regex.matcher(line).matches()) {
                        style = Style.Info
                        warnings.add(line)
                        break
                    }
                }

                for (regex in errorRegexes) {
                    if (regex.matcher(line).matches()) {
                        style = Style.Failure
                        errors.add(line)
                        break
                    }
                }

                output.withStyle(style) { println(line) }
            }

            reset()
        }
    }

    fun summarise() {
        output.println()
        if (errors.size > 0) {
            summariseMessages(errors, "errors", Style.Failure)
        }
        if (warnings.size > 0) {
            summariseMessages(warnings, "warnings", Style.Info)
        }
    }

    private fun summariseMessages(messages: Collection<String>, type: String, style: Style) {
        val LINE_LENGTH = 75
        val msg = " ${projectName}: ${messages.size} ${type} "
        val firstDashes = (LINE_LENGTH - msg.length) / 2
        val secondDashes = (LINE_LENGTH - msg.length) - firstDashes
        output.withStyle(style) {
            println("=".repeat(firstDashes) + msg + "=".repeat(secondDashes))
            for (it in messages) {
                println(it)
            }
            println("=".repeat(LINE_LENGTH))
        }
        output.println()
    }
}

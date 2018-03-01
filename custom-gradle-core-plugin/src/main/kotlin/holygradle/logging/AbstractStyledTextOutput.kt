package holygradle.logging

import holygradle.gradle.api.FunctionAction

abstract class AbstractStyledTextOutput : StyledTextOutput {
    final override fun withStyle(style: StyledTextOutput.Style, action: StyledTextOutput.() -> Unit) {
        withStyle(style, FunctionAction(action))
    }
}
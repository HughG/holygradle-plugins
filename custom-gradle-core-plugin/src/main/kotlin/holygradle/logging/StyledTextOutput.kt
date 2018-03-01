package holygradle.logging

import holygradle.gradle.api.FunctionAction
import org.gradle.api.Action

/**
 * Copyright (c) 2016 Hugh Greene (githugh@tameter.org).
 */
interface StyledTextOutput {

    fun println(str: String)

    fun println()

    fun withStyle(style: StyledTextOutput.Style, action: StyledTextOutput.() -> Unit)

    fun withStyle(style: StyledTextOutput.Style, action: Action<StyledTextOutput>)

    enum class Style(val asString: String) {
        Normal("     "),
        Header("hdr  "),
        UserInput("input"),
        Identifier("id   "),
        Description("dsc  "),
        ProgressStatus("...  "),
        Success("OK   "),
        SuccessHeader("OKh  "),
        Failure("FAIL "),
        FailureHeader("FAILh"),
        Info("Inf  "),
        Error("ERR  ");

        override fun toString(): String = asString
    }
}

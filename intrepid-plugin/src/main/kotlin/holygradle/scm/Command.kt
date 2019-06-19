package holygradle.scm

import org.gradle.api.Action
import org.gradle.process.ExecSpec
import java.util.function.Predicate

internal interface Command {
    fun execute(configureExecSpec: Action<ExecSpec>): String
    fun execute(configureExecSpec: Action<ExecSpec>, throwForExitValue: Predicate<Int>): String
}
package holygradle.scm

import org.gradle.api.Action
import org.gradle.process.ExecSpec
import java.util.function.Predicate

interface Command {
    fun execute(configureExecSpec: Action<ExecSpec>): String
    fun execute(configureExecSpec: Action<ExecSpec>, throwOnError: Predicate<Int>): String
}
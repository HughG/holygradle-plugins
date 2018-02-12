package holygradle.scm

import org.gradle.api.Action
import org.gradle.process.ExecSpec

interface Command {
    fun execute(configureExecSpec: Action<ExecSpec>): String
    fun execute(configureExecSpec: Action<ExecSpec>, throwOnError: (Int) -> Boolean): String
}
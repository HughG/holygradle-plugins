package holygradle.scm

import holygradle.gradle.api.invoke
import holygradle.process.ExecHelper
import org.gradle.api.Action
import org.gradle.api.logging.Logger
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.util.function.Predicate

class CommandLine(
        private val logger: Logger,
        private val path: String,
        private val exec: (Action<in ExecSpec>) -> ExecResult
) : Command {
    override fun execute(configureExecSpec: Action<ExecSpec>, throwOnError: Predicate<Int>): String {
        return ExecHelper.executeAndReturnResultAsString(logger, exec, throwOnError) {
            executable(path)
            configureExecSpec(this)
        }
    }

    override fun execute(configureExecSpec: Action<ExecSpec>): String {
        return execute(configureExecSpec, Predicate { true })
    }
}

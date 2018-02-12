package holygradle.scm

import holygradle.gradle.api.invoke
import holygradle.process.ExecHelper
import org.gradle.api.Action
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.util.function.Predicate

class CommandLine(
        private val path: String,
        private val exec: (Action<in ExecSpec>) -> ExecResult
) : Command {
    override fun execute(configureExecSpec: Action<ExecSpec>, throwOnError: Predicate<Int>): String {
        return ExecHelper.executeAndReturnResultAsString(
            exec,
            Action { spec: ExecSpec ->
                spec.executable(path)
                configureExecSpec(spec)
            },
            throwOnError
        )
    }

    override fun execute(configureExecSpec: Action<ExecSpec>): String {
        return execute(configureExecSpec, Predicate { true })
    }
}

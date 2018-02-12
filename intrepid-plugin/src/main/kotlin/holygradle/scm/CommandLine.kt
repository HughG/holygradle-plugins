package holygradle.scm

import holygradle.process.ExecHelper
import org.gradle.api.Action
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class CommandLine(
        private val path: String,
        private val exec: (Action<in ExecSpec>) -> ExecResult
) : Command {
    override fun execute(configureExecSpec: Action<ExecSpec>, throwOnError: (Int) -> Boolean): String {
        return ExecHelper.executeAndReturnResultAsString(
            exec,
            { spec: ExecSpec ->
                spec.executable(path)
                configureExecSpec(spec)
            },
            throwOnError
        )
    }

    override fun execute(configureExecSpec: Action<ExecSpec>): String {
        return execute(configureExecSpec, { true })
    }
}

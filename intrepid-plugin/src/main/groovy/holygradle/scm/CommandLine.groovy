package holygradle.scm

import holygradle.process.ExecHelper
import org.gradle.api.Task
import org.gradle.api.tasks.TaskState
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class CommandLine implements Command {
    private final String hgPath
    private final Closure<ExecResult> exec

    CommandLine(
        String hgPath,
        Closure<ExecResult> exec
    ) {
        this.hgPath = hgPath
        this.exec = exec
    }

    @Override
    String execute(Closure configureExecSpec, Closure throwOnError) {
        String localHgPath = hgPath
        ExecHelper.executeAndReturnResultAsString(
            exec,
            { ExecSpec spec ->
                spec.executable localHgPath
                configureExecSpec(spec)
            },
            throwOnError
        )
    }

    @Override
    String execute(Closure configureExecSpec) {
        execute(configureExecSpec, {return true})
    }
}

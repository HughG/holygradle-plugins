package holygradle.scm

import holygradle.process.ExecHelper
import org.gradle.api.logging.Logger
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class CommandLine implements Command {
    private final String hgPath
    private final Closure<ExecResult> exec
    private final Logger logger

    CommandLine(Logger logger, String hgPath, Closure<ExecResult> exec) {
        this.logger = logger
        this.hgPath = hgPath
        this.exec = exec
    }

    @Override
    String execute(Closure configureExecSpec, Closure throwForExitValue) {
        String localHgPath = hgPath
        ExecHelper.executeAndReturnResultAsString(logger, exec, throwForExitValue) { ExecSpec spec ->
            spec.executable localHgPath
            configureExecSpec(spec)
        }
    }

    @Override
    String execute(Closure configureExecSpec) {
        execute(configureExecSpec, {return true})
    }
}

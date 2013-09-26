package holygradle.scm

import holygradle.process.ExecHelper
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class HgCommandLine implements HgCommand {

    private final String hgPath
    private final Closure<ExecResult> exec

    HgCommandLine(
        String hgPath,
        Closure<ExecResult> exec
    ) {
        this.hgPath = hgPath
        this.exec = exec
    }

    @Override
    String execute(Collection<String> args) {
        String localHgPath = hgPath
        ExecHelper.executeAndReturnResultAsString(exec) { ExecSpec spec ->
            spec.executable localHgPath
            spec.args args
        }
    }
}

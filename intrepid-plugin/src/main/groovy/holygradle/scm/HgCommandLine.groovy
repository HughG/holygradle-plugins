package holygradle.scm

import holygradle.process.ExecHelper
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class HgCommandLine implements HgCommand {

    private final String hgPath
    private final Closure<ExecResult> exec
    private final String hgrcPath

    HgCommandLine(
        String hgPath,
        File hgrcPath,
        Closure<ExecResult> exec
    ) {
        this.hgPath = hgPath
        this.exec = exec
        this.hgrcPath = hgrcPath.exists() ? hgrcPath.path : ""
    }

    @Override
    String execute(Collection<String> args) {
        String localHgrcPath = hgrcPath
        String localHgPath = hgPath
        ExecHelper.executeAndReturnResultAsString(exec) { ExecSpec spec ->
            if (localHgPath.length() > 0) {
                spec.environment.put("HGRCPATH", localHgrcPath)
            }
            spec.executable localHgPath
            spec.args args
        }
    }
}

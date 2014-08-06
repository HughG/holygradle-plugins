package holygradle.scm

import holygradle.process.ExecHelper
import org.gradle.api.Task
import org.gradle.api.tasks.TaskState
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class HgCommandLine implements HgCommand {
    private final Task hgUnpackTask
    private final String hgPath
    private final Closure<ExecResult> exec

    HgCommandLine(
        Task hgUnpackTask,
        String hgPath,
        Closure<ExecResult> exec
    ) {
        this.hgUnpackTask = hgUnpackTask
        this.hgPath = hgPath
        this.exec = exec
    }

    @Override
    String execute(Closure configureExecSpec) {
        TaskState hgUnpackTaskState = hgUnpackTask.state
        if (!(hgUnpackTaskState.skipped || hgUnpackTaskState.executed)) {
            throw new RuntimeException(
                "${this.class.name} cannot run because ${hgUnpackTask.name} has not been executed (or skipped). " +
                "The current task may be missing a dependency on ${hgUnpackTask.name}, normally from " +
                "SourceControlRepository#getToolSetupTask(). This may be a bug in the holygradle plugins."
            )
        }
        String localHgPath = hgPath
        ExecHelper.executeAndReturnResultAsString(exec) { ExecSpec spec ->
            spec.executable localHgPath
            configureExecSpec(spec)
        }
    }
}

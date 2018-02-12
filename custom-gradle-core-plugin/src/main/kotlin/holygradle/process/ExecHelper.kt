package holygradle.process

import org.gradle.api.Action
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import java.util.function.Predicate

/**
 * Utility methods related to executing processes.
 */
object ExecHelper {
    fun executeAndReturnResultAsString(
            execMethod: (Action<ExecSpec>) -> ExecResult,
            configureSpec: Action<ExecSpec>,
            throwOnError: Predicate<Int>
    ): String {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val execResult = execMethod(Action { spec ->
            configureSpec.execute(spec)
            spec.standardOutput = stdout
            spec.errorOutput = stderr
            spec.isIgnoreExitValue = true
        })
        val exitValue = execResult.exitValue
        if ((exitValue != 0) && throwOnError.test(exitValue)) {
            execResult.rethrowFailure()
        }
        return stdout.toString().trim()
    }
}

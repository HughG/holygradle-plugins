package holygradle.process

import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

/**
 * Utility methods related to executing processes.
 */
class ExecHelper {
    public static executeAndReturnResultAsString(
        Closure<ExecResult> execMethod,
        Closure configureSpec,
        Closure throwOnError
    ) {
        OutputStream stdout = new ByteArrayOutputStream()
        OutputStream stderr = new ByteArrayOutputStream()
        ExecResult execResult = execMethod { ExecSpec spec ->
            configureSpec(spec)
            spec.setStandardOutput stdout
            spec.setErrorOutput stderr
            spec.setIgnoreExitValue true
        }
        int exit_value = execResult.getExitValue()
        if ((exit_value != 0) && throwOnError(exit_value)) {
            execResult.rethrowFailure()
        }
        stdout.toString().trim();
    }
}

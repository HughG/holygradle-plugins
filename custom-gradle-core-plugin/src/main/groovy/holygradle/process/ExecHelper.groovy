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
        Closure throwForExitValue
    ) {
        OutputStream stdout = new ByteArrayOutputStream()
        OutputStream stderr = new ByteArrayOutputStream()
        ExecResult execResult = execMethod { ExecSpec spec ->
            configureSpec(spec)
            spec.setStandardOutput stdout
            spec.setErrorOutput stderr
            spec.setIgnoreExitValue true
        }
        int exitValue = execResult.getExitValue()
        if ((exitValue != 0) && throwForExitValue(exitValue)) {
            execResult.assertNormalExitValue()
        }
        stdout.toString().trim()
    }
}

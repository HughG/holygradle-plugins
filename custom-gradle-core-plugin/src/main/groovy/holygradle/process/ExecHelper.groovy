package holygradle.process

import org.gradle.api.logging.Logger
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

/**
 * Utility methods related to executing processes.
 */
class ExecHelper {
    public static executeAndReturnResultAsString(
        Logger logger,
        Closure<ExecResult> execMethod,
        Closure configureSpec,
        Closure throwForExitValue
    ) {
        String commandLine = null

        OutputStream stdout = new ByteArrayOutputStream()
        OutputStream stderr = new ByteArrayOutputStream()
        ExecResult execResult = execMethod { ExecSpec spec ->
            configureSpec(spec)
            spec.setStandardOutput stdout
            spec.setErrorOutput stderr
            spec.setIgnoreExitValue true

            commandLine = spec.commandLine
        }
        int exitValue = execResult.getExitValue()
        def shouldThrow = throwForExitValue(exitValue)

        if (exitValue != 0) {
            logger.debug "Will ${shouldThrow ? '' : 'not '}throw for exitValue ${exitValue} from commandLine ${commandLine}."
            logger.debug "Error output stream follows. >>>"
            stderr.toString().eachLine { logger.debug it }
            logger.debug "<<< Error output stream ends."

            if (shouldThrow) {
                execResult.assertNormalExitValue()
            }
        }
        stdout.toString().trim()
    }
}

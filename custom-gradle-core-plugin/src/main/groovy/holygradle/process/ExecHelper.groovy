package holygradle.process

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

/**
 * Utility methods related to executing processes.
 */
class ExecHelper {
    public static String executeAndReturnResultAsString(
        Logger logger,
        Closure<ExecResult> execMethod,
        Closure configureSpec
    ) {
        return executeAndReturnResultAsString(logger, execMethod, { return true }, configureSpec)
    }

    public static String executeAndReturnResultAsString(
        Logger logger,
        Closure<ExecResult> execMethod,
        Closure throwForExitValue,
        Closure configureSpec
    ) {
        def streams = execute(logger, execMethod, throwForExitValue, configureSpec)
        return streams.stdout.toString().trim()
    }

    public static Map<String, OutputStream> execute(
        Logger logger,
        Closure<ExecResult> execMethod,
        Closure configureSpec
    ) {
        return execute(logger, execMethod, { return true }, configureSpec)
    }

    public static Map<String, OutputStream> execute(
        Logger logger,
        Closure<ExecResult> execMethod,
        Closure throwForExitValue,
        Closure configureSpec
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
            LogLevel logLevel = (shouldThrow ? LogLevel.ERROR : LogLevel.INFO)
            logger.info "Will ${shouldThrow ? '' : 'not '}throw for exitValue ${exitValue} from commandLine ${commandLine}."
            if (shouldThrow) {
                logger.error "Failed with exitValue ${exitValue} from commandLine ${commandLine}."
            }
            [
                "Standard": stdout,
                "Error": stderr
            ].each { name, stream ->
                logger.log logLevel, "${name} output stream follows. >>>"
                stream.toString().eachLine { logger.log logLevel, it }
                logger.log logLevel, "<<< ${name} output stream ends."

            }

            if (shouldThrow) {
                execResult.assertNormalExitValue()
            }
        }
        return [
            "stdout": stdout,
            "stderr": stderr
        ]
    }
}

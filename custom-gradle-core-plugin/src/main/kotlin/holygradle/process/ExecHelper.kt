package holygradle.process

import org.gradle.api.Action
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.function.Predicate

class ProcessOutputStreams(val stdout: OutputStream, val stderr: OutputStream)

/**
 * Utility methods related to executing processes.
 */
object ExecHelper {
    @JvmStatic
    fun executeAndReturnResultAsString(
            logger: Logger,
            execMethod: (Action<ExecSpec>) -> ExecResult,
            throwOnError: Predicate<Int> = Predicate { true },
            configureSpec: Action<ExecSpec>
    ): String {
        return execute(logger, execMethod, throwOnError, { configureSpec.execute(this) } ).stdout.toString().trim()
    }

    fun executeAndReturnResultAsString(
            logger: Logger,
            execMethod: (Action<ExecSpec>) -> ExecResult,
            throwOnError: Predicate<Int> = Predicate { true },
            configureSpec: ExecSpec.() -> Unit
    ): String {
        return execute(logger, execMethod, throwOnError, configureSpec).stdout.toString().trim()
    }

    @JvmStatic
    fun execute(
            logger: Logger,
            execMethod: (Action<ExecSpec>) -> ExecResult,
            throwOnError: Predicate<Int> = Predicate { true },
            configureSpec: Action<ExecSpec>
    ): ProcessOutputStreams {
        return execute(logger, execMethod, throwOnError, { configureSpec.execute(this) })
    }

    fun execute(
            logger: Logger,
            execMethod: (Action<ExecSpec>) -> ExecResult,
            throwOnError: Predicate<Int> = Predicate { true },
            configureSpec: ExecSpec.() -> Unit
    ): ProcessOutputStreams {
        var commandLine: List<String>? = null

        val stdout: OutputStream = ByteArrayOutputStream()
        val stderr: OutputStream = ByteArrayOutputStream()
        val execResult = execMethod(Action { spec ->
            spec.configureSpec()
            spec.standardOutput = stdout
            spec.errorOutput = stderr
            spec.isIgnoreExitValue = true

            commandLine = spec.commandLine
        })
        val exitValue = execResult.exitValue
        val shouldThrow = throwOnError.test(exitValue)

        if (exitValue != 0) {
            val logLevel = if (shouldThrow) LogLevel.ERROR else LogLevel.INFO
            logger.info("Will ${if (shouldThrow) "" else "not "}throw for exitValue ${exitValue} from commandLine ${commandLine}.")
            if (shouldThrow) {
                logger.error("Failed with exitValue ${exitValue} from commandLine ${commandLine}.")
            }
            mapOf(
                "Standard" to stdout,
                "Error" to stderr
            ).forEach { name, stream ->
                logger.log(logLevel, "${name} output stream follows. >>>")
                stream.toString().lineSequence().forEach { logger.log(logLevel, it) }
                logger.log(logLevel, "<<< ${name} output stream ends.")
            }

            if (shouldThrow) {
                execResult.assertNormalExitValue()
            }
        }
        return ProcessOutputStreams(stdout, stderr)
    }
}

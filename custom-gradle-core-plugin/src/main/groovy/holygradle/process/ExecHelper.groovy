package holygradle.process

import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

/**
 * Utility methods related to executing processes.
 */
class ExecHelper {
    public static executeAndReturnResultAsString(Closure<ExecResult> execMethod, Closure configureSpec) {
        OutputStream stdout = new ByteArrayOutputStream()
        OutputStream stderr = new ByteArrayOutputStream()
        ExecResult execResult = execMethod { ExecSpec spec ->
            configureSpec(spec)
            spec.setStandardOutput stdout
            spec.setErrorOutput stderr
            spec.setIgnoreExitValue true
        }
        int exit_value = execResult.getExitValue()
        if (exit_value != 0) {
            throw new ExecuteAndReturnStringException(stderr.toString().trim(), exit_value)
        }
        stdout.toString().trim();
    }
}

/**
 * Custom exception which allows code to check the exit value for this specific situation
 */
class ExecuteAndReturnStringException extends RuntimeException {
    private int exit_value;

    public ExecuteAndReturnStringException(String message, int exit_value) {
        super(message)
        this.exit_value = exit_value
    }
    public int getExitValue() {
        return exit_value
    }
}

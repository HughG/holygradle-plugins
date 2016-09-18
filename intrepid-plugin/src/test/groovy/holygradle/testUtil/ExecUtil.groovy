package holygradle.testUtil

import org.gradle.process.ExecSpec

/**
 * Utilities related to Gradle-style execution of things.
 */
class ExecUtil {
    /**
     * Returns a new instance of a stub implementation of ExecSpec.  The instance only implements the following.
     * <ul>
     *     <li>args</li>
     *     <li>getArgs</li>
     *     <li>getWorkingDir</li>
     *     <li>setWorkingDir</li>
     * </ul>
     * @return A new instance of a stub implementation of ExecSpec.
     */
    public static ExecSpec makeStubExecSpec() {
        ExecSpec dummySpec
        Object[] actualArgs = null
        File actualWorkingDir = null
        dummySpec = [
            // Note: For some reason, if there's only one argument, the args method gets its varargs as an array of one
            // element, containing the actual varargs array.
            args: { Object... theArgs -> actualArgs = (theArgs.length == 1 ? theArgs[0] : theArgs); return dummySpec },
            getArgs : { actualArgs.toList() },
            setWorkingDir: { File dir -> actualWorkingDir = dir },
            getWorkingDir: { actualWorkingDir }
        ] as ExecSpec
        return dummySpec
    }
}

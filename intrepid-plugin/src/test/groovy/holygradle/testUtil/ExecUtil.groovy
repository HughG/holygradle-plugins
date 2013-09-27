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
            // Note: for some reason, the args method gets its varargs as an array of one element, containing the actual
            // varargs array; maybe because it's overloaded for (Object...) and (Iterable)?
            args: { Object... theArgs -> actualArgs = theArgs[0]; return dummySpec },
            getArgs : { actualArgs.toList() },
            setWorkingDir: { File dir -> actualWorkingDir = dir },
            getWorkingDir: { actualWorkingDir }
        ] as ExecSpec
        return dummySpec
    }
}

package holygradle.custom_gradle

import org.gradle.BuildResult

public class BuildHelper {
    public static boolean buildFailedDueToVersionConflict(BuildResult result) {
        Throwable e = result.failure
        while (e != null) {
            final String message = e.message
            if (message != null && message.startsWith("A conflict was found between the following modules:")) {
                return true
            }
            e = e.cause
        }
        return false
    }
}

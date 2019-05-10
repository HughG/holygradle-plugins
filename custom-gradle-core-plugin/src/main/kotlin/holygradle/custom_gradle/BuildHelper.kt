package holygradle.custom_gradle

import org.gradle.BuildResult

object BuildHelper {
    fun buildFailedDueToVersionConflict(result: BuildResult): Boolean {
        var e: Throwable? = result.failure
        while (e != null) {
            val message = e.message
            if (message != null && message.startsWith("A conflict was found between the following modules:")) {
                return true
            }
            e = e.cause
        }
        return false
    }
}


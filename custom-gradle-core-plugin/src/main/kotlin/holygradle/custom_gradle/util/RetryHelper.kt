package holygradle.custom_gradle.util

import org.gradle.api.logging.Logger

object RetryHelper {
    fun retry(times: Int, delayInMillis: Long, logger00: Logger?, description: String, action: () -> Unit) {
        var lastException00: Exception? = null
        for (i in times downTo 0) {
            try {
                action()
                return
            } catch (e: Exception) {
                lastException00 = e
            }
            logger00?.info("Action failed, retrying ${times} more times: ${description}")
            Thread.sleep(delayInMillis)
        }
        val message = "Action failed after ${times} retries: ${description}"
        if (lastException00 == null) {
            throw RuntimeException(message)
        } else {
            throw RuntimeException(message, lastException00)
        }
    }
}

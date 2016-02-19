package holygradle.custom_gradle.util

import org.gradle.api.logging.Logger

public class RetryHelper {
    public static void retry(int times, long delayInMillis, Logger logger00, String description, Closure action) {
        Exception lastException00 = null
        for (int i = times; i >= 0; --i) {
            try {
                action()
                return
            } catch (Exception e) {
                lastException00 = e
            }
            logger00?.info("Action failed, retrying ${times} more times: ${description}")
            Thread.sleep(delayInMillis)
        }
        String message = "Action failed after ${times} retries: ${description}"
        if (lastException00 == null) {
            throw new RuntimeException(message)
        } else {
            throw new RuntimeException(message, lastException00)
        }
    }
}

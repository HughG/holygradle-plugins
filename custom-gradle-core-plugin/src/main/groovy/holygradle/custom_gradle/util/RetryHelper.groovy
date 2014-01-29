package holygradle.custom_gradle.util

import org.gradle.api.logging.Logger

public class RetryHelper {
    public static void retry(int times, long delayInMillis, Logger logger, String description, Closure action) {
        for (int i = times; i >= 0; --i) {
            if (action()) {
                return;
            }
            logger.info("Action failed, retrying ${times} more times: ${description}")
            Thread.sleep(delayInMillis)
        }
        throw new RuntimeException("Action failed after ${times} retries: ${description}")
    }
}

package holygradle.custom_gradle.util

import org.gradle.api.logging.Logger

import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 * Helper class for profiling the time taken by calls and other blocks of code.
 */
class ProfilingHelper {
    public static DateFormat ELAPSED_TIME_TO_MILLIS_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS")

    public static class TimedBlock {
        private final long startNanos = System.nanoTime()
        private final Logger logger
        private final String name

        public TimedBlock(Logger logger, String name) {
            this.logger = logger
            this.name = name
        }

        public void endBlock() {
            final long endNanos = System.nanoTime()
            final long diffNanos = endNanos - startNanos
            final long diffJustNanos = diffNanos % 1000
            final long diffMicros = diffNanos / 1000
            final long diffJustMicros = diffMicros % 1000
            final long diffMillis = diffMicros / 1000
            final String formattedMillis = ELAPSED_TIME_TO_MILLIS_FORMAT.format(new Date(diffMillis))

            logger.info("TimedBlock\t${name}\t${startNanos}\t${endNanos}\t${diffNanos}\t${formattedMillis},${diffJustMicros},${diffJustNanos}")
        }
    }

    private final Logger logger

    public ProfilingHelper(Logger logger) {
        this.logger = logger
    }

    public TimedBlock startBlock(String blockName) {
        return new TimedBlock(logger, blockName)
    }

    public void timing(String blockName, Closure block) {
        def timer = startBlock(blockName)
        block()
        timer.endBlock()
    }
}

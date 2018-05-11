package holygradle.custom_gradle.util

import groovy.lang.Closure
import org.gradle.api.logging.Logger
import holygradle.kotlin.dsl.invoke

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class for profiling the time taken by calls and other blocks of code.
 */
class ProfilingHelper(private val logger: Logger) {
    companion object {
        val ELAPSED_TIME_TO_MILLIS_FORMAT: DateFormat = SimpleDateFormat("HH:mm:ss.SSS")
    }

    class TimedBlock(private val logger: Logger, private val name: String) {
        private val startNanos = System.nanoTime()

        fun endBlock() {
            val endNanos = System.nanoTime()
            val diffNanos = endNanos - startNanos
            val diffJustNanos = diffNanos % 1000
            val diffMicros = diffNanos / 1000
            val diffJustMicros = diffMicros % 1000
            val diffMillis = diffMicros / 1000
            val formattedMillis = ELAPSED_TIME_TO_MILLIS_FORMAT.format(Date(diffMillis))

            logger.info("TimedBlock\t${name}\t${startNanos}\t${endNanos}\t${diffNanos}\t${formattedMillis},${diffJustMicros},${diffJustNanos}")
        }
    }

    fun startBlock(blockName: String): TimedBlock {
        return TimedBlock(logger, blockName)
    }

    fun timing(blockName: String, block: Closure<Any>) {
        val timer = startBlock(blockName)
        try {
            block()
        } finally {
            timer.endBlock()
        }
    }
}

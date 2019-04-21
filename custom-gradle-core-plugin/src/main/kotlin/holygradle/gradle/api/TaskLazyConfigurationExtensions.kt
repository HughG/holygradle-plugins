package holygradle.gradle.api

import holygradle.custom_gradle.util.ProfilingHelper
import holygradle.kotlin.dsl.extra
import org.gradle.api.Task

private const val LAZY_CONFIGURATION_EXTRA_PROPERTY_NAME = "holygradle.lazyConfiguration"

fun <T : Task, V: Any> T.lazyConfiguration(action: T.() -> V) {
    this.extra[LAZY_CONFIGURATION_EXTRA_PROPERTY_NAME] = action
}

fun <T : Task> T.executeLazyConfiguration() {
    if (hasProperty(LAZY_CONFIGURATION_EXTRA_PROPERTY_NAME)) {
        // In the unlikely event that someone set the lazyConfiguration property to an Action<SomethingElse>,
        // we'll get ClassCastException later.  Should never happen.
        val lazyConfigRaw = property(LAZY_CONFIGURATION_EXTRA_PROPERTY_NAME)
        @Suppress("UNCHECKED_CAST")
        val lazyConfig = lazyConfigRaw as? (Task.() -> Any?)
        if (lazyConfig != null) {
            logger.info("Applying lazy configuration for task ${name}.")
            ProfilingHelper(logger).timing("${name}#!${LAZY_CONFIGURATION_EXTRA_PROPERTY_NAME}") {
                lazyConfig()
            }
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            setProperty(LAZY_CONFIGURATION_EXTRA_PROPERTY_NAME, null)
        } else {
            if (lazyConfigRaw != null) {
                this.logger.error("Task ${this.name} has non-Action<> ${LAZY_CONFIGURATION_EXTRA_PROPERTY_NAME} " +
                        "property value ${lazyConfigRaw} of type ${lazyConfigRaw::class}")

            }
        }
    }
}


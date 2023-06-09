package holygradle.gradle.api

import org.gradle.api.Action

/**
 * Copyright (c) 2018 Hugh Greene (githugh@tameter.org).
 */
@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> Action<T>.invoke(t: T) = execute(t)

class FunctionAction<T>(private val function: T.() -> Unit) : Action<T> {
    override fun execute(t: T) {
        function(t)
    }
}
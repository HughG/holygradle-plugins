package holygradle

/**
 * Copyright (c) 2016 Hugh Greene (githugh@tameter.org).
 */

@Deprecated("Use Kotlin map instead", ReplaceWith("this.map(action)"))
fun <T, U> Iterable<T>.collect(action: (T) -> U): List<U> {
    return map(action)
}

@Deprecated("Use Kotlin forEach instead", ReplaceWith("this.forEach(action)"))
inline fun <T> Iterable<T>.each(action: (T) -> Unit) {
    forEach(action)
}

@Deprecated("Use Kotlin String.repeat", ReplaceWith("this.repeat(n)"))
operator fun String.times(n: Int): String {
    return repeat(n)
}

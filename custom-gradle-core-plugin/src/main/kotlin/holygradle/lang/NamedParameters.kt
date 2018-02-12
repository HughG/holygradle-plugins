package holygradle.lang

import org.jetbrains.kotlin.utils.keysToMap

object NamedParameters {
    /**
     * This object should be used as the value for a key in the {@code parameterSpecs} passed to
     * {@link #checkAndGet(java.util.Map, java.util.Map)} to specify that a named argument is allowed but has no
     * default.  This allows {@code null} to be a valid default value.
     */
    val NO_DEFAULT = Object()

    /**
     * Given a map of named arguments, this method checks that only permitted names are used, and optionally supplies
     * default values for some names for which values did not appear in the original map.  It then returns a list of
     * values, in the order in which the keys appear in the @{code parameterSpecs}.  This allows callers to bind a
     * separate variable for each argument, with the usual Groovy multi-value assignment syntax.
     *
     * The @{code parameterSpecs} is map in which the keys are permitted argument names, and the values are the
     * corresponding default values (or {@link #NO_DEFAULT} to indicate that a value must be explicitly supplied if the
     * argument is used).
     *
     * @param attrs A {@link Map} of optional arguments.
     * @param parameterSpecs A list of allowed arguments, each optionally with a default.
     * @return A list of values for each of the specified parameters, optionally with defaults filled in.
     */
    fun checkAndGet(attrs: Map<String, Any>, parameterSpecs: Map<String, Any>): List<Any?> {
        val expectedNames = parameterSpecs.keys
        val unexpectedNames = attrs.keys - expectedNames
        if (unexpectedNames.isNotEmpty()) {
            throw IllegalArgumentException(
                "Got unexpected named arguments: ${unexpectedNames.keysToMap { attrs[it] }}; " +
                "expected only ${expectedNames}"
            )
        }
        val lazyMissingDefaults = lazy { ArrayList<String>(parameterSpecs.size) }
        val missingDefaults: MutableList<String> by lazyMissingDefaults
        val result = ArrayList<Any?>(parameterSpecs.size)
        for ((k, v) in parameterSpecs) {
            when {
                attrs.containsKey(k) -> result.add(attrs[k])
                v != NO_DEFAULT -> result.add(v)
                else -> missingDefaults.add(k)
            }
        }
        if (lazyMissingDefaults.isInitialized()) {
            throw IllegalArgumentException(
                "Got named arguments: ${attrs}; value required but not supplied for ${missingDefaults}"
            )
        }
        return result
    }
}

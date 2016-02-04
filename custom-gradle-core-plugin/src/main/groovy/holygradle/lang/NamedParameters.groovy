package holygradle.lang

public class NamedParameters {
    /**
     * This object should be used as the value for a key in the {@code parameterSpecs} passed to
     * {@link #checkAndGet(java.util.Map, java.util.List)} to specify that a named argument is allowed but has no
     * default.  This allows {@code null} to be a valid default value.
     */
    public static final Object NO_DEFAULT = new Object()

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
    public static List checkAndGet(Map attrs, Map<String, Object> parameterSpecs) {
        Collection<String> expectedNames = parameterSpecs.keySet()
        Collection<String> unexpectedNames = attrs.keySet() - expectedNames
        if (!unexpectedNames.empty) {
            throw new IllegalArgumentException(
                "Got unexpected named arguments: ${unexpectedNames.collectEntries { [it, attrs[it]] }}; " +
                "expected only ${expectedNames}"
            )
        }
        Collection missingDefaults = null
        Collection result = new ArrayList(parameterSpecs.size())
        parameterSpecs.each { k, v ->
            if (attrs.containsKey(k)) {
                result << attrs[k]
            } else if (v != NO_DEFAULT) {
                result << v
            } else {
                if (missingDefaults == null) {
                    missingDefaults = new ArrayList(parameterSpecs.size())
                }
                missingDefaults << k
            }
        }
        if (missingDefaults != null) {
            throw new IllegalArgumentException(
                "Got named arguments: ${attrs}; value required but not supplied for ${missingDefaults}"
            )
        }
        return result
    }
}

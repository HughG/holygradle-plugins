package holygradle.lang

public class NamedParameters {
    /**
     * Given a map of optional arguments, this method checks that only permitted names are used, and optionally supplies
     * default values for some names for which values did not appear in the original map.  It then returns a list of
     * values, in the order in which the keys appear in the @{code parameterSpecs}.  This allows callers to bind a
     * separate variable for each argument, with the usual Groovy multi-value assignment syntax.
     *
     * The @{code parameterSpecs} is a list of list of strings.  Each entry is either a one-element list, containing
     * just a parameter name, or a two-element list, containing a parameter name and a default value in that order.
     *
     * @param attrs A {@link Map} of optional arguments.
     * @param parameterSpecs A list of allowed arguments, each optionally with a default.
     * @return A list of values for each of the specified parameters, optionally with defaults filled in.
     */
    public static List checkAndGet(Map attrs, List<List<String>> parameterSpecs) {
        Collection<String> expectedNames = parameterSpecs*.get(0)
        Collection<String> unexpectedNames = attrs.keySet() - expectedNames
        if (!unexpectedNames.empty) {
            throw new IllegalArgumentException(
                "Got unexpected named arguments: ${unexpectedNames.collectEntries { [it, attrs[it]] }}; " +
                "expected only ${expectedNames}"
            )
        }
        return parameterSpecs.collect { attrs[it[0]] ?: (it.size() > 1 ? it[1] : null) }
    }
}

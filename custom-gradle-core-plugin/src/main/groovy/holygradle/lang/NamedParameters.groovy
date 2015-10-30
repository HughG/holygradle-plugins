package holygradle.lang

public class NamedParameters {
    public static List checkAndGet(Map attrs, List<List<String>> parameterNames) {
        Collection unexpectedNames = attrs.keySet() - parameterNames*.get(0)
        if (!unexpectedNames.empty) {
            throw new IllegalArgumentException(
                "Got unexpected named arguments: ${unexpectedNames.collectEntries { [it, attrs[it]] }}; " +
                "expected only ${parameterNames}"
            )
        }
        return parameterNames.collect { attrs[it[0]] ?: (it.size() > 1 ? [1] : null) }
    }
}

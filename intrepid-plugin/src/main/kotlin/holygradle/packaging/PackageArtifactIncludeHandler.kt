package holygradle.packaging

class PackageArtifactIncludeHandler(vararg includePatterns: String) {
    private val _includePatterns = mutableListOf<String>().apply { addAll(includePatterns) }
    val includePatterns: Collection<String> get() = _includePatterns
    private val _replacements = mutableMapOf<String, String>()
    val replacements: Map<String, String> get() = _replacements

    fun replace(find: String, replace: String) {
        _replacements[find] = replace
    }
}
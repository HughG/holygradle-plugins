package holygradle.packaging

class PackageArtifactIncludeHandler {
    public Collection<String> includePatterns = []
    public Map<String, String> replacements = [:]
    
    PackageArtifactIncludeHandler(String... includePatterns) {
        this.includePatterns.addAll(includePatterns)
    }
    
    public void replace(String find, String replace) {
        replacements[find] = replace
    }
}
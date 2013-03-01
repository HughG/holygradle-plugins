package holygradle

class PackageArtifactIncludeHandler {
    public def includePatterns = []
    public def replacements = [:]
    
    PackageArtifactIncludeHandler(def includePatterns) {
        includePatterns.each { inc ->
            this.includePatterns.add(inc)
        }
    }
    
    public void replace(String find, String replace) {
        replacements[find] = replace
    }
}
package holygradle.packaging

public interface PackageArtifactBaseDSL {
    PackageArtifactIncludeHandler include(String... patterns)
    
    void include(String pattern, Closure closure)
    
    void includeBuildScript(Closure closure)
    
    void includeTextFile(String path, Closure closure)
    
    void includeSettingsFile(Closure closure)
    
    void exclude(String... patterns)

    void from(String fromLocation)
    
    void from(String fromLocation, Closure closure)
    
    void to(String toLocation)
}
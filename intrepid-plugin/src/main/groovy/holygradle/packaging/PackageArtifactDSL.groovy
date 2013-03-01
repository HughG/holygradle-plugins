package holygradle

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.bundling.*
import org.gradle.util.ConfigureUtil

interface PackageArtifactDSL {
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
package holygradle.packaging

import org.gradle.api.Action

interface PackageArtifactBaseDSL {
    fun include(vararg patterns: String): PackageArtifactIncludeHandler

    fun include(pattern: String, action: Action<PackageArtifactIncludeHandler>)
    
    fun includeBuildScript(action: Action<in PackageArtifactBuildScriptHandler>)
    
    fun includeTextFile(path: String, action: Action<PackageArtifactPlainTextFileHandler>)
    
    fun includeSettingsFile(action: Action<PackageArtifactSettingsFileHandler>)

    var createDefaultSettingsFile: Boolean

    fun exclude(vararg patterns: String)

    fun from(fromLocation: String)
    
    fun from(fromLocation: String, action: Action<PackageArtifactDescriptor>)
    
    fun to(toLocation: String)
}
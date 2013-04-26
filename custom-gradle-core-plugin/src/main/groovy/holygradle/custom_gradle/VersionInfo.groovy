package holygradle.custom_gradle
import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ModuleVersionIdentifier
class VersionInfo {
    private Project project
    private def versions = [:]
    private def buildscriptDependencies = null
    private String windowsUpdates = null
    
    public static VersionInfo defineExtension(Project project) {
        if (project == project.rootProject) {
            project.extensions.create("versionInfo", VersionInfo, project)
        } else {
            project.extensions.add("versionInfo", project.rootProject.extensions.findByName("versionInfo"))
        }
    }
    
    VersionInfo(Project project) {
        this.project = project
        
        specifyPowershell("Windows", "[System.Environment]::OSVersion.VersionString.Trim()")
        specify("gradle", project.gradle.gradleVersion)
        specify("custom-gradle", project.gradle.gradleHomeDir.parentFile.parentFile.name.split("-")[-1])
        if (project.hasProperty("initScriptVersion")) {
            specify("custom-gradle (init script)", project.ext.initScriptVersion)
        }
    }
    
    public void specify(String item, String version) {
        versions[item] = version
    }
    
    public void specifyPowershell(String item, String powershellCommand) {
        def powershellOutput = new ByteArrayOutputStream()
        def execResult = project.exec {
            commandLine "powershell", "-Command", powershellCommand
            setStandardOutput powershellOutput
            setErrorOutput new ByteArrayOutputStream()
            setIgnoreExitValue true
        }
        specify(item, powershellOutput.toString().trim())
    }
    // This returns a map from the resolved versions of all Gradle build script dependencies to the versions originally
    // requested in the "gplugins" block (which is a custom-gradle convention), or "none" if they were transitive
    // dependencies not explicitly requested or were pulled in in some other way.
    public def getBuildscriptDependencies() {
        if (buildscriptDependencies == null) {
            def gplugins = project.extensions.findByName("gplugins")
            buildscriptDependencies = [:]
            project.getBuildscript().getConfigurations().each { conf ->
                conf.resolvedConfiguration.getResolvedArtifacts().each { ResolvedArtifact art ->
                    ModuleVersionIdentifier depModuleVersion = art.getModuleVersion().getId()
                    def requestedVersion = "none"
                    
                    if (gplugins != null) {
                        gplugins.usages.each { pluginName, pluginVersion ->
                            if (depModuleVersion.getName().startsWith(pluginName)) {
                                requestedVersion = pluginVersion
                            }
                        }
                    }     
        
                    buildscriptDependencies[depModuleVersion] = requestedVersion
                }
            }
        }
        buildscriptDependencies
    }
    
    public String getVersion(String plugin) {
        versions[plugin]
    }
    
    public def getVersions() {
        versions
    }
    
    private String getWindowsUpdates() {
        if (windowsUpdates == null) {
            def wmicOutput = new ByteArrayOutputStream()
            def execResult = project.exec {
                commandLine "wmic", "qfe", "list"
                setStandardOutput wmicOutput
                setErrorOutput new ByteArrayOutputStream()
                setIgnoreExitValue true
            }
            windowsUpdates = wmicOutput.toString()
        }
        windowsUpdates
    }
    
    public void writeFile(File file) {
        StringBuilder str = new StringBuilder()
        
        str.append "Versions\n"
        str.append "========\n"
        getVersions().each { item, version ->
            str.append "${item} : ${version}\n"
        }
        getBuildscriptDependencies().each { version, requestedVersionStr ->
            str.append "${version.getName()} : ${version.getVersion()} (requested: $requestedVersionStr)\n"
        }
        str.append "\n"
        
        def updates = getWindowsUpdates()
        if (updates != null) {
            str.append "Windows Updates\n"
            str.append "===============\n"
            str.append updates
            str.append "\n"
        }
        
        file.write(str.toString())
    }
}

package holygradle.custom_gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.process.ExecSpec

class VersionInfo {
    private Project project
    private Map<String, String> versions = [:]
    private Map<ModuleVersionIdentifier,String> buildscriptDependencies = null
    private String windowsUpdates = null
    
    public static VersionInfo defineExtension(Project project) {
        if (project == project.rootProject) {
            return project.extensions.create("versionInfo", VersionInfo, project)
        } else {
            VersionInfo rootProjectExtension = project.rootProject.extensions.findByName("versionInfo") as VersionInfo
            project.extensions.add("versionInfo", rootProjectExtension)
            return rootProjectExtension
        }
    }
    
    VersionInfo(Project project) {
        this.project = project
        
        specify("gradle", project.gradle.gradleVersion)
        specify("custom-gradle", project.gradle.gradleHomeDir.parentFile.parentFile.name.split("-")[-1])
        if (project.hasProperty("holyGradleInitScriptVersion")) {
            specify("custom-gradle (init script)", project.holyGradleInitScriptVersion as String)
        }
    }
    
    public void specify(String item, String version) {
        versions[item] = version
    }
    
    public void specifyPowerShell(String item, String powerShellCommand) {
        OutputStream powerShellOutput = new ByteArrayOutputStream()
        project.exec { ExecSpec it ->
            it.commandLine "powershell", "-Command", powerShellCommand
            it.setStandardOutput powerShellOutput
            it.setErrorOutput new ByteArrayOutputStream()
            it.setIgnoreExitValue true
        }
        specify(item, powerShellOutput.toString().trim())
    }
    // This returns a map from the resolved versions of all Gradle build script dependencies to the versions originally
    // requested in the "gplugins" block (which is a custom-gradle convention), or "none" if they were transitive
    // dependencies not explicitly requested or were pulled in in some other way.
    public Map<ModuleVersionIdentifier,String> getBuildscriptDependencies() {
        if (buildscriptDependencies == null) {
            PluginUsages pluginUsagesExtension = project.extensions.findByName("pluginUsages") as PluginUsages
            buildscriptDependencies = pluginUsagesExtension.mapping.entries.collectEntries { m ->
                String moduleName = m.key
                PluginUsages.Versions versions = m.value
                return [new DefaultModuleVersionIdentifier("holygradle", moduleName, versions.selected), versions.requested]
            }
        }
        buildscriptDependencies
    }
    
    public String getVersion(String plugin) {
        versions[plugin]
    }
    
    public Map<String, String> getVersions() {
        versions
    }
    
    private String getWindowsUpdates() {
        if (windowsUpdates == null) {
            OutputStream wmicOutput = new ByteArrayOutputStream()
            project.exec { ExecSpec it ->
                it.commandLine "wmic", "qfe", "list"
                it.setStandardOutput wmicOutput
                it.setErrorOutput new ByteArrayOutputStream()
                it.setIgnoreExitValue true
            }
            windowsUpdates = wmicOutput.toString()
        }
        windowsUpdates
    }
    
    public void writeFile(File file) {
        // We avoid calling specifyPowerShell in the constructor because that will run on every build where this plugin
        // is used, adding a few seconds to startup time.  Calling it here only runs it when it's needed.
        specifyPowerShell("Windows", "[System.Environment]::OSVersion.VersionString.Trim()")

        StringBuilder str = new StringBuilder()
        
        str.append "Versions\n"
        str.append "========\n"
        getVersions().each { item, version ->
            str.append "${item} : ${version}\n"
        }
        getBuildscriptDependencies().each { version, requestedVersionStr ->
            str.append "${version.name} : ${version.version} (requested: $requestedVersionStr)\n"
        }
        str.append "\n"
        
        String updates = getWindowsUpdates()
        if (updates != null) {
            str.append "Windows Updates\n"
            str.append "===============\n"
            str.append updates
            str.append "\n"
        }
        
        file.write(str.toString())
    }
}

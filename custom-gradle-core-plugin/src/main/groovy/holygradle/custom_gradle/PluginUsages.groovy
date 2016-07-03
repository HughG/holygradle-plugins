package holygradle.custom_gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency

class PluginUsages {
    private Project project
    private Map<String,String> usages = [:]
    
    PluginUsages(Project project) {
        this.project = project
        
        Map<String,String> gpluginsUsages = project.extensions.findByName("gplugins")?.usages
        if (gpluginsUsages != null && gpluginsUsages.size() > 0) {
            Map<String, String> pluginVersions = [:]
            ResolvedConfiguration classpathResolvedConfiguration =
                project.buildscript.configurations['classpath'].resolvedConfiguration
            classpathResolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency dep ->
                gpluginsUsages.keySet().each { String plugin ->
                    if (dep.moduleName.startsWith(plugin)) {
                        pluginVersions[plugin] = dep.moduleVersion
                    }
                }
            }
            usages = pluginVersions
        }
    }
    
    public String getVersion(String plugin) {
        usages[plugin]
    }
    
    public Map<String, String> getMapping() {
        usages
    }
}

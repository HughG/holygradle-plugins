package holygradle.custom_gradle

import org.gradle.api.Project

class PluginUsages {
    private Project project
    private Map<String,String> usages = [:]
    
    PluginUsages(Project project) {
        this.project = project
        
        Map<String,String> gpluginsUsages = project.extensions.findByName("gplugins")?.usages
        if (gpluginsUsages != null && gpluginsUsages.size() > 0) {
            def pluginVersions = [:]
            project.getBuildscript().getConfigurations().each { conf ->
                conf.resolvedConfiguration.getFirstLevelModuleDependencies().each { dep ->
                    gpluginsUsages.keySet().each { plugin ->
                        if (dep.getModuleName().startsWith(plugin)) {
                            pluginVersions[plugin] = dep.getModuleVersion()
                        }
                    }
                }
            }
            usages = pluginVersions
        }            
    }
    
    public String getVersion(String plugin) {
        usages[plugin]
    }
    
    public def getMapping() {
        usages
    }
}

package holygradle.custom_gradle
import org.gradle.*
import org.gradle.api.*
class PluginUsages {
    private Project project
    private def usages = [:]
    
    PluginUsages(Project project) {
        this.project = project
        
        def gplugins = project.extensions.findByName("gplugins")
        if (gplugins != null && gplugins.usages.size() > 0) {
            def pluginVersions = [:]
            project.getBuildscript().getConfigurations().each { conf ->
                conf.resolvedConfiguration.getFirstLevelModuleDependencies().each { dep ->
                    project.gplugins.usages.keySet().each { plugin ->
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

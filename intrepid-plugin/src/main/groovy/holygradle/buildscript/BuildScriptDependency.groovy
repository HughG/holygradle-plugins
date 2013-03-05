package holygradle

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.tasks.*

class BuildScriptDependency {
    private String dependencyName
    private Task unpackTask = null
    private File dependencyPath = null
    
    public BuildScriptDependency(Project project, String dependencyName, boolean needsUnpacked) {
        this.dependencyName = dependencyName
        def dependencyArtifact = null
        project.getBuildscript().getConfigurations().each { conf ->
            conf.resolvedConfiguration.getFirstLevelModuleDependencies().each { resolvedDependency ->
                resolvedDependency.getAllModuleArtifacts().each { art ->
                    if (art.getName().startsWith(dependencyName)) {
                        dependencyArtifact = art
                    }
                }
            }
        }
        
        if (needsUnpacked) {
            def moduleVersion = dependencyArtifact.getModuleVersion().getId()
            unpackTask = project.task(getUnpackTaskName(), type: Copy) {
                from project.zipTree(dependencyArtifact.getFile())
                into Helper.getGlobalUnpackCacheLocation(project, moduleVersion)
            }
            
            dependencyPath = unpackTask.destinationDir
        } else {
            dependencyPath = dependencyArtifact.getFile()
        }
    }
    
    public Task getUnpackTask() {
        unpackTask
    }
    
    public String getUnpackTaskName() {
        Helper.MakeCamelCase("extract", dependencyName)
    }
    
    public File getPath() {
        dependencyPath
    }
}

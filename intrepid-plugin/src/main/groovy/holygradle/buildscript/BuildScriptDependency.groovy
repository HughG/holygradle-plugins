package holygradle

import holygradle.util.*
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
            if (dependencyArtifact == null) { 
                unpackTask = project.task(getUnpackTaskName(), type: DefaultTask)
            } else {
                def unpackCacheLocation = Helper.getGlobalUnpackCacheLocation(project, dependencyArtifact.getModuleVersion().getId())
                dependencyPath = unpackCacheLocation
                unpackTask = project.task(getUnpackTaskName(), type: DefaultTask) {
                    ext.destinationDir = unpackCacheLocation
                    // Checking if the target dir exists is a pretty crude way to choose whether or not to do
                    // the unpacking. Normally the 'copy' operation would do this for us, but if another instance
                    // of Gradle in another command prompt is using this dependency (e.g. extracting a package 
                    // using 7zip) then the copy operation would fail. So this is really a step towards supporting
                    // intrepid concurrency but it's not really correct. a) it's still possible for two instances
                    // to execute 'copy' at the same time, and b) just because the directory exists doesn't necessarily
                    // mean it's got the right stuff in it (but running 'copy' would fix it up).
                    // Anyway, just this simple check is good enough for now.
                    if (!dependencyPath.exists()) {
                        project.copy {
                            from project.zipTree(dependencyArtifact.getFile())
                            into unpackCacheLocation
                        }
                    }
                }
            }
        } else if (dependencyArtifact != null) {
            dependencyPath = dependencyArtifact.getFile()
        }
    }
    
    public Task getUnpackTask() {
        unpackTask
    }
    
    public String getUnpackTaskName() {
        // Unfortunately can't use the CamelCase helper from custom-gradle-core because we're
        // executing this from the buildscript block before the custom-gradle-core-plugin has
        // been added to the classpath.
        Helper.MakeCamelCase("extract", dependencyName)
    }
    
    public File getPath() {
        dependencyPath
    }
}

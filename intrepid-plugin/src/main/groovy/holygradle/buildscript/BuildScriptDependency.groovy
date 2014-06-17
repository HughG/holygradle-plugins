package holygradle.buildscript

import holygradle.Helper
import holygradle.custom_gradle.util.CamelCase
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.Copy

class BuildScriptDependency {
    private String dependencyName
    private Task unpackTask = null
    private File dependencyPath = null
    
    public BuildScriptDependency(Project project, String dependencyName, boolean needsUnpacked) {
        this.dependencyName = dependencyName
        ResolvedArtifact dependencyArtifact = null
        project.buildscript.configurations.each { Configuration conf ->
            conf.resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency resolvedDependency ->
                resolvedDependency.allModuleArtifacts.each { ResolvedArtifact art ->
                    if (art.name.startsWith(dependencyName)) {
                        dependencyArtifact = art
                    }
                }
            }
        }
        
        if (needsUnpacked) {
            if (dependencyArtifact == null) {
                unpackTask = project.task(getUnpackTaskName(), type: DefaultTask)
            } else {
                File unpackCacheLocation = Helper.getGlobalUnpackCacheLocation(project, dependencyArtifact.getModuleVersion().getId())
                dependencyPath = unpackCacheLocation
                unpackTask = project.task(getUnpackTaskName(), type: Copy) { Copy it ->
                    it.ext.destinationDir = unpackCacheLocation
                    it.from project.zipTree(dependencyArtifact.getFile())
                    it.into unpackCacheLocation
                }
                unpackTask.onlyIf {
                    // Checking if the target dir exists is a pretty crude way to choose whether or not to do
                    // the unpacking. Normally the 'copy' operation would do this for us, but if another instance
                    // of Gradle in another command prompt is using this dependency (e.g. extracting a package
                    // using 7zip) then the copy operation would fail. So this is really a step towards supporting
                    // intrepid concurrency but it's not really correct. a) it's still possible for two instances
                    // to execute 'copy' at the same time, and b) just because the directory exists doesn't necessarily
                    // mean it's got the right stuff in it (but running 'copy' would fix it up).
                    // Anyway, just this simple check is good enough for now.
                    !dependencyPath.exists()
                }
            }
            unpackTask.description = "Unpack build dependency '${dependencyName}'"
        } else if (dependencyArtifact != null) {
            dependencyPath = dependencyArtifact.getFile()
        }
    }
    
    public Task getUnpackTask() {
        unpackTask
    }

    public String getUnpackTaskName() {
        CamelCase.build(["extract", dependencyName] as String[])
    }
    
    public File getPath() {
        dependencyPath
    }
}

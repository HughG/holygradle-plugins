package holygradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection

class CopyArtifactsHandler {
    public String target = null
    public def configurations = []
    public def includes = []
    public def excludes = []
    
    CopyArtifactsHandler() {
    }
    
    public void configuration(String... configs) {
        configs.each { String config ->
            configurations.add(config)
        }
    }
    
    public void include(String... inc) {
        inc.each { includes.add(it) }
    }
    
    public void exclude(String... exc) {
        exc.each { excludes.add(it) }
    }
    
    public def gatherSourceWildcardPaths(Project project) {
        project.packageArtifacts.each { packArt ->
            if (configurations.contains(packArt.configuration.toString())) {
                
            }
        }
    }
    
    public static def gatherFiles(CopySpec spec) {
        def files = null
        spec.getAllSpecs().each { childSpec ->
            if (files == null) {
                files = childSpec.getSource().files
            } else {
                files = files.plus childSpec.getSource().files
            }
        }
        files
    }
    
    public static def createExtension(Project project) {
        project.extensions.create("copyArtifacts", CopyArtifactsHandler)
    }
    
    public static void collectZipsForConfigurations(Project project, def configurations, def artifactZips) {
        UnpackModule.getAllUnpackModules(project).each { module ->
            module.versions.each { versionStr, version ->
                version.artifacts.each { art, confs ->
                    if (!confs.disjoint(configurations)) {
                        artifactZips.add(art.getFile())
                    }
                }
            }
        }
    }
    public Task defineCopyTask(Project project) {
        def copyArtifactsExtension = project.copyArtifacts
        def copyTask = null
        
        if (copyArtifactsExtension.target != null && copyArtifactsExtension.configurations.size() > 0) {
            copyTask = project.task("copyArtifacts", type: Copy) {
                group = "Source Dependencies"
                description = "Copy ${copyArtifactsExtension.configurations} artifacts to '${project.name}/${copyArtifactsExtension.target}'."
                ext.lazyConfiguration = {
                    def copySpec = project.copySpec { }
                    def artifactZips = []
                    project.sourceDependencies.each { sourceDep ->
                        def sourceDepProject = project.rootProject.findProject(sourceDep.getTargetName())
                        sourceDepProject.packageArtifacts.each { packArt ->
                            if (copyArtifactsExtension.configurations.contains(packArt.configuration.toString())) {
                                packArt.configureCopySpec(sourceDepProject, copySpec)
                            }
                        }
                        collectZipsForConfigurations(sourceDepProject, copyArtifactsExtension.configurations, artifactZips)
                    }
                    collectZipsForConfigurations(project, copyArtifactsExtension.configurations, artifactZips)
                    into(new File(project.projectDir, copyArtifactsExtension.target))
                    from gatherFiles(copySpec)
                    artifactZips.each { zip ->
                        from project.zipTree(zip).files
                    }
                    includes = copyArtifactsExtension.includes 
                    excludes = copyArtifactsExtension.excludes 
                }
            }
        }
        
        copyTask
    }
}

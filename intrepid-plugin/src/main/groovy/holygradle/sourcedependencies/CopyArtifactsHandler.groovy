package holygradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.util.ConfigureUtil

class CopyArtifactsFromHandler {
    public String dependencyName
    public def configurations = []
    public def includes = []
    public def excludes = []
    public String relativePath = null
    
    public CopyArtifactsFromHandler(String dependencyName) {
        this.dependencyName = dependencyName
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
}

class CopyArtifactsHandler {
    public String target = null
    public def fromHandlers = []
    
    CopyArtifactsHandler() {
    }
    
    public void from(Closure closure) {
        from(null, closure)
    }
    
    public void from(String dependencyName, Closure closure) {
        if (dependencyName != null) {
            // Rules for specific dependencies should be specified first, before any catch-all rule
            // for which the dependencyName is null.
            for (f in fromHandlers) {
                if (f.dependencyName != null) {
                    throw new RuntimeException("Error: in copyArtifacts, please state specific cases before the general case i.e. from(\"foo\") { } before from { }.")
                }
            }
        } else {
            // Only allow one general case
            for (f in fromHandlers) {
                if (f.dependencyName == null) {
                    throw new RuntimeException("Error: in copyArtifacts, please only specify one general case i.e 'from' with no parameters. If you want you can call from(\"foo\") { } and from(\"bar\") { } but this must be before you specify the general case.")
                }
            }
        }
    
        def handler = new CopyArtifactsFromHandler(dependencyName)
        ConfigureUtil.configure(closure, handler)
        fromHandlers.add(handler)
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
    
    public static void collectZipsForConfigurations(Project project, def alreadyHandled, String dependencyName, def configurations, def artifactZips) {
        UnpackModule.getAllUnpackModules(project).each { module ->
            if (dependencyName == null || dependencyName == module.name) {
                if (!alreadyHandled.contains(module.name)) {
                    alreadyHandled.add(module.name)
                    module.versions.each { versionStr, version ->
                        version.artifacts.each { art, confs ->
                            if (!confs.disjoint(configurations)) {
                                artifactZips.add(art.getFile())
                            }
                        }
                    }
                }
            }
        }
    }
    
    public Task defineCopyTask(Project project) {
        def copyArtifactsExtension = project.copyArtifacts
        def copyTask = null
        
        if (copyArtifactsExtension.fromHandlers.size() > 0) {
            copyTask = project.task("copyArtifacts", type: Copy) {
                group = "Source Dependencies"
                description = "Copy artifacts from dependencies to specified location. Usage: gw copyArtifacts -DcopyArtifactsTarget=D:\\path\\to\\target."
                ext.lazyConfiguration = {
                    def copyArtifactsTarget = System.getProperty("copyArtifactsTarget")
                    if (copyArtifactsTarget == null) {
                        throw new RuntimeException("In order to invoke the copyArtifacts task please specify copyArtifactsTarget on the command line e.g. gw copyArtifacts -DcopyArtifactsTarget=D:\\path\\to\\the\\target. Make sure it's an absolute path.")
                    }
                    def targetDir = new File(copyArtifactsTarget)
                    logger.info("copyArtifactsTarget was set to: ${targetDir}")
                    
                    into targetDir
                    
                    def alreadyHandled = []
                    copyArtifactsExtension.fromHandlers.each { f ->
                        def copySpec = project.copySpec { }
                        def artifactZips = []
                        project.sourceDependencies.each { sourceDep ->
                            def sourceDepName = sourceDep.getTargetName()
                            def sourceDepProject = project.rootProject.findProject(sourceDepName)
                            if (f.dependencyName == null || f.dependencyName == sourceDepName) {
                                if (!alreadyHandled.contains(sourceDepName)) {
                                    alreadyHandled.add(sourceDepName)
                                    sourceDepProject.packageArtifacts.each { packArt ->
                                        if (f.configurations.contains(packArt.configuration.toString())) {
                                            packArt.configureCopySpec(sourceDepProject, copySpec)
                                        }
                                    }
                                }
                                collectZipsForConfigurations(sourceDepProject, alreadyHandled, f.dependencyName, f.configurations, artifactZips)
                            }
                        }
                        collectZipsForConfigurations(project, alreadyHandled, f.dependencyName, f.configurations, artifactZips)
                        def sourceDepFiles = gatherFiles(copySpec)
                        logger.info("copyArtifacts selected these packed dependency files: ${artifactZips}")
                        logger.info("copyArtifacts selected these source dependency files: ${sourceDepFiles}")
                        
                        def intoTarget = targetDir
                        if (f.relativePath != null) {
                            intoTarget = new File(targetDir, f.relativePath)
                        }
                        
                        if (sourceDepFiles.size() > 0) {
                            println "sourceDepFiles: $sourceDepFiles"
                            from(sourceDepFiles) {
                                includes = f.includes 
                                excludes = f.excludes 
                            }
                        }
                        
                        artifactZips.each { zip ->
                            println "from: " + project.zipTree(zip)
                            println "into: $intoTarget"
                            
                            from(project.zipTree(zip)) {
                                includes = f.includes 
                                excludes = f.excludes 
                            }
                        }
                    }
                }
            }
        }
        
        copyTask
    }
}

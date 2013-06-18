package holygradle.source_dependencies

import holygradle.packaging.PackageArtifactHandler
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.file.CopySpec
import org.gradle.util.ConfigureUtil
import holygradle.unpacking.UnpackModule

class CopyArtifactsFromHandler {
    public String dependencyName
    public Collection<String> configurations = []
    public Collection<String> includes = []
    public Collection<String> excludes = []
    public String relativePath = null
    
    public CopyArtifactsFromHandler(String dependencyName) {
        this.dependencyName = dependencyName
    }
    
    public void configuration(String... configs) {
        configurations.addAll(configs)
    }
    
    public void include(String... inc) {
        includes.addAll(inc)
    }
    
    public void exclude(String... exc) {
        excludes.addAll(exc)
    }
}

class CopyArtifactsHandler {
    public String target = null
    public Collection<CopyArtifactsFromHandler> fromHandlers = []
    
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
    
        CopyArtifactsFromHandler handler = new CopyArtifactsFromHandler(dependencyName)
        ConfigureUtil.configure(closure, handler)
        fromHandlers.add(handler)
    }

    // TODO 2013-06-18 HughG: Can't cleanly add static type info to this, because it's using internal APIs.  But, I
    // don't think we need this function as is.  Will re-visit after doing the rest of the static type changes.
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
    
    public static CopyArtifactsHandler createExtension(Project project) {
        project.extensions.create("copyArtifacts", CopyArtifactsHandler)
    }
    
    public static void collectZipsForConfigurations(
        Project project,
        Collection<String> alreadyHandled,
        String dependencyName,
        Collection<String> configurations,
        Collection<File> artifactZips
    ) {
        UnpackModule.getAllUnpackModules(project).each { module ->
            if (dependencyName == null || dependencyName == module.name) {
                if (!alreadyHandled.contains(module.name)) {
                    alreadyHandled.add(module.name)
                    module.versions.each { versionStr, version ->
                        version.artifacts.each { ResolvedArtifact art, Collection<String> confs ->
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
        CopyArtifactsHandler copyArtifactsExtension = project.copyArtifacts
        Task copyTask = null
        
        if (copyArtifactsExtension.fromHandlers.size() > 0) {
            copyTask = project.task("copyArtifacts", type: Copy) { Copy task ->
                task.group = "Source Dependencies"
                task.description = "Copy artifacts from dependencies to specified location. Usage: gw copyArtifacts -DcopyArtifactsTarget=D:\\path\\to\\target."
                task.ext.lazyConfiguration = {
                    String copyArtifactsTarget = System.getProperty("copyArtifactsTarget")
                    if (copyArtifactsTarget == null) {
                        throw new RuntimeException("In order to invoke the copyArtifacts task " +
                            "please specify copyArtifactsTarget on the command line. " +
                            "E.g. gw copyArtifacts -DcopyArtifactsTarget=D:\\path\\to\\the\\target. " +
                            "Make sure it's an absolute path."
                        )
                    }
                    File targetDir = new File(copyArtifactsTarget)
                    logger.info("copyArtifactsTarget was set to: ${targetDir}")
                    
                    task.into targetDir

                    final Collection<SourceDependencyHandler> sourceDependencies =
                        project.sourceDependencies as Collection<SourceDependencyHandler>
                    Collection<String> alreadyHandled = []
                    copyArtifactsExtension.fromHandlers.each { f ->
                        CopySpec copySpec = project.copySpec { }
                        Collection<File> artifactZips = []

                        sourceDependencies.each { sourceDep ->
                            String sourceDepName = sourceDep.getTargetName()
                            Project sourceDepProject = project.rootProject.findProject(sourceDepName)
                            if (f.dependencyName == null || f.dependencyName == sourceDepName) {
                                if (!alreadyHandled.contains(sourceDepName)) {
                                    alreadyHandled.add(sourceDepName)
                                    final Collection<PackageArtifactHandler> packageArtifactsHandlers =
                                        sourceDepProject.packageArtifacts as Collection<PackageArtifactHandler>
                                    packageArtifactsHandlers.each { PackageArtifactHandler packArt ->
                                        if (f.configurations.contains(packArt.configurationName)) {
                                            packArt.configureCopySpec(sourceDepProject, copySpec)
                                        }
                                    }
                                }
                                collectZipsForConfigurations(
                                    sourceDepProject,
                                    alreadyHandled,
                                    f.dependencyName,
                                    f.configurations,
                                    artifactZips
                                )
                            }
                        }
                        collectZipsForConfigurations(project, alreadyHandled, f.dependencyName, f.configurations, artifactZips)
                        def sourceDepFiles = gatherFiles(copySpec)
                        logger.info("copyArtifacts selected these packed dependency files: ${artifactZips}")
                        logger.info("copyArtifacts selected these source dependency files: ${sourceDepFiles}")
                        
                        File intoTarget = targetDir
                        if (f.relativePath != null) {
                            intoTarget = new File(targetDir, f.relativePath)
                        }
                        
                        if (sourceDepFiles.size() > 0) {
                            task.from(sourceDepFiles) {
                                includes = f.includes 
                                excludes = f.excludes 
                            }
                        }
                        
                        artifactZips.each { zip ->
                            task.from (project.zipTree(zip).files) {
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

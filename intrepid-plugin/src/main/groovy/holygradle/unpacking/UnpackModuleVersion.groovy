package holygradle.unpacking

import holygradle.Helper
import holygradle.custom_gradle.util.CamelCase
import holygradle.dependencies.PackedDependencyHandler
import holygradle.symlinks.SymlinkTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact

class UnpackModuleVersion {
    public ModuleVersionIdentifier moduleVersion = null
    public boolean includeVersionNumberInPath = false
    public Map<ResolvedArtifact, Collection<String>> artifacts = [:] // a map from artifacts to sets of configurations that include the artifacts
    private Map<String, String> dependencyRelativePaths = [:]
    private UnpackModuleVersion parentUnpackModuleVersion
    private PackedDependencyHandler packedDependency00 = null
    
    UnpackModuleVersion(
        ModuleVersionIdentifier moduleVersion,
        File ivyFile,
        UnpackModuleVersion parentUnpackModuleVersion,
        PackedDependencyHandler packedDependency00
    ) {
        this.moduleVersion = moduleVersion
        this.parentUnpackModuleVersion = parentUnpackModuleVersion
        
        this.packedDependency00 = packedDependency00
        if (packedDependency00 != null) {
            this.includeVersionNumberInPath = packedDependency00.pathIncludesVersionNumber()
        }
        
        // Read the relative paths for any of the dependencies.  (No point trying to use static types here as we're
        // using GPath, so the returned objects have magic properties.)
        def ivyXml = new XmlSlurper(false, false).parseText(ivyFile.text)
        ivyXml.dependencies.dependency.each { dependencyNode ->
            def relativePath = dependencyNode.@relativePath
            if (relativePath != null) {
                dependencyRelativePaths[
                    "${dependencyNode.@org}:${dependencyNode.@name}:${dependencyNode.@rev}"
                ] = relativePath.toString()
            }
        }
    }
    
    public String getIncludeInfo() {
        if (includeVersionNumberInPath) {
            "(includes version number)"
        } else {
            "(no version number)"
        }
    }
    
    public void addArtifacts(Iterable<ResolvedArtifact> arts, String conf) {
        for (art in arts) {
            if (artifacts.containsKey(art)) {
                if (!artifacts[art].contains(conf)) {
                    artifacts[art].add(conf)
                }
            } else {
                artifacts[art] = [conf]
            }
        }
    }
    
    public String getFullCoordinate() {
        "${moduleVersion.getGroup()}:${moduleVersion.getName()}:${moduleVersion.getVersion()}"
    }
    
    // This method returns the relative path specified in the Ivy file from this module to 
    // the given module.
    private String getRelativePathForDependency(UnpackModuleVersion moduleVersion) {
        String coordinate = moduleVersion.getFullCoordinate()
        if (dependencyRelativePaths.containsKey(coordinate)) {
            return dependencyRelativePaths[coordinate]
        } else {
            // If we don't know what the relative path is from this module to the dependency
            // (i.e. it's not specified in the Ivy), then fall-back to the old behaviour which
            // was to put the dependency immediately under the workspace root.
            return "/"
        }
    }
    
    // This returns the packedDependencies entry corresponding to this dependency. This will
    // return null if no such entry exists, which would be the case if this is a transitive
    // dependency.
    public PackedDependencyHandler getPackedDependency() {
        packedDependency00
    }
    
    // TODO: will need to track all parents because each one (unpacked to a different location in
    // the central cache) will need to create a symlink to this module in the cache.
    public UnpackModuleVersion getParent() {
        parentUnpackModuleVersion
    }
    
    // Return a fully configured task for unpacking the module artifacts to the appropriate location.
    // This could be to the central cache or directly to the workspace.
    public Task getUnpackTask(Project project) {
        boolean shouldApplyUpToDateChecks = false
        PackedDependencyHandler packedDependency = getPackedDependency()
        if (packedDependency != null) {
            shouldApplyUpToDateChecks = packedDependency.shouldApplyUpToDateChecks()
        }
        
        String taskName = CamelCase.build("extract", moduleVersion.getName()+moduleVersion.getVersion())
        Task unpackTask = project.tasks.findByName(taskName)
        if (unpackTask == null) {
            // The task hasn't already been defined, so we should define it.
            boolean speedy = !shouldApplyUpToDateChecks
            if (project.buildScriptDependencies.getPath("sevenZip") == null) {
                speedy = false
            }
            if (speedy) {
                unpackTask = project.task(taskName, type: SpeedyUnpackTask) { SpeedyUnpackTask task ->
                    task.initialize(project, getUnpackDir(project), getPackedDependency(), artifacts.keySet())
                }
            } else {
                unpackTask = project.task(taskName, type: UnpackTask) { UnpackTask task ->
                    task.initialize(project, getUnpackDir(project), artifacts.keySet())
                }
            }
            unpackTask.description = getUnpackDescription()
        }
        
        unpackTask
    }
    
    // Collect all parent, grand-parent, etc symlink tasks for creating symlinks in the workspace, pointing
    // to the central cache.
    public Collection<Task> collectParentSymlinkTasks(Project project) {
        Collection<Task> symlinkTasks = []
        if (parentUnpackModuleVersion != null) {
            parentUnpackModuleVersion.collectParentSymlinkTasks(project).each { 
                symlinkTasks.add(it)
            }
        }
        symlinkTasks.add(getSymlinkTaskIfUnpackingToCache(project))
        symlinkTasks
    }
    
    // This method returns a fully configured symlink task for creating a symlink in the workspace, pointing
    // to the central cache. The task will depend on any symlink tasks for parent, grand-parent modules.
    // Null will be returned if this module is not unpacked to the cache.
    public Task getSymlinkTaskIfUnpackingToCache(Project project) {
        Task symlinkTask = null
        if (shouldUnpackToCache()) {
            String taskName = CamelCase.build("symlink", moduleVersion.getName()+moduleVersion.getVersion())
            
            symlinkTask = project.tasks.findByName(taskName)
            if (symlinkTask == null) {
                File linkDir = getTargetPathInWorkspace(project)
                symlinkTask = project.task(taskName, type: SymlinkTask) {
                    group = "Dependencies"
                    description = "Build workspace-to-cache symlink for ${moduleVersion.getName()}:${moduleVersion.getVersion()}."
                }
                symlinkTask.configure(project, linkDir, getUnpackDir(project))
            }
            
            // Define dependencies from this symlink task to the symlink tasks for parent modules.
            // This ensures that the unpacking is done in a specific order, which is very useful because
            // it means that when 
            if (parentUnpackModuleVersion != null) {
                parentUnpackModuleVersion.collectParentSymlinkTasks(project).each {
                    symlinkTask.dependsOn it
                }
            }
        }
        symlinkTask
    }
    
    // This returns the packedDependencies entry which configures some aspects of this module.
    // The entry could be specifically for this module, in the case where a project directly
    // specifies a dependency. Or this entry could be for a 'parent' packedDependency entry 
    // i.e. one which causes this module to be pulled in as a transitive dependency.
    public PackedDependencyHandler getParentPackedDependency() {
        if (packedDependency00 != null) {
            return packedDependency00
        }
        
        // If we don't return packedDependency00 above then this must be a transitive dependency.
        // Therefore we must have a parent. Not having a parent is an error.
        if (parentUnpackModuleVersion == null) {
            throw new RuntimeException("Error - module '${getFullCoordinate()}' has no parent module.")
        }
        return parentUnpackModuleVersion.getParentPackedDependency()
    }
    
    // Return the name of the directory that should be constructed in the workspace for the unpacked artifacts. 
    // Depending on some other configuration, this directory name could be used for a symlink or a real directory.
    public String getTargetDirName() {
        if (packedDependency00 != null) {
            return packedDependency00.getTargetNameWithVersionNumber(moduleVersion.getVersion())
        } else if (includeVersionNumberInPath) {
            moduleVersion.getName() + "-" + moduleVersion.getVersion()
        } else {
            moduleVersion.getName()
        }
    }
    
    // Return the full path of the directory that should be constructed in the workspace for the unpacked artifacts. 
    // Depending on some other configuration, this path could be used for a symlink or a real directory.
    public File getTargetPathInWorkspace(Project project) {
        if (packedDependency00 != null) {
            // If we have a packed dependency then we can directly construct the target path.
            // We don't need to go looking through transitive dependencies.
            String targetPath = packedDependency00.getFullTargetPathWithVersionNumber(moduleVersion.getVersion())
            if (project == null) {
                return new File(targetPath)
            } else {
                return new File(project.projectDir, targetPath)
            }
        } else {
            // If we don't return above then this must be a transitive dependency.
            // Therefore we must have a parent. Not having a parent is an error.
            if (parentUnpackModuleVersion == null) {
                GString msg = "Error - module '${getFullCoordinate()}' has no parent module. "
                if (parent != null) {
                    msg += "(Project: ${project.name})"
                }
                throw new RuntimeException(msg)
            }
            
            String relativePathForDependency = parentUnpackModuleVersion.getRelativePathForDependency(this)
            if (relativePathForDependency == "" ||
                relativePathForDependency.endsWith("/") || 
                relativePathForDependency.endsWith("\\")
            ) {
                // If the relative path is empty, or ends with a slash then assume that the path does not indicate  
                // the name of the directory for the module, but only indicates the path to the parent directory.
                // So we need to add the name of the target directory ourselves.
                relativePathForDependency = relativePathForDependency + getTargetDirName() 
            }
            
            if (relativePathForDependency.startsWith("/") || 
                relativePathForDependency.startsWith("\\")
            ) {
                // If there is no 'relativePath' or it begins with a slash then revert to the behaviour
                // of making the path relative to the root project.
                if (project == null) {
                    return new File(relativePathForDependency)
                } else {
                    return new File(project.rootProject.projectDir, relativePathForDependency)
                }
            } else {
                // Recursively navigate up the parent hierarchy, appending relative paths.
                return new File(parentUnpackModuleVersion.getTargetPathInWorkspace(project), relativePathForDependency)
            }
        }
    }
    
    // If true this module should be unpacked to the central cache, otherwise it should be unpacked
    // directly to the workspace.
    private boolean shouldUnpackToCache() {
        getParentPackedDependency().shouldUnpackToCache()
    }
    
    // Return the location to which the artifacts will be unpacked. This could be to the global unpack 
    // cache or it could be to somewhere in the workspace.
    private File getUnpackDir(Project project) {
        if (shouldUnpackToCache()) {
            // Our closest packed-dependency entry (which could be for 'this' module, or any parent module)
            // dictated that we should unpack to the global cache.
            Helper.getGlobalUnpackCacheLocation(project, moduleVersion)
        } else {
            // We're unpacking directly into the workspace.
            getTargetPathInWorkspace(project)
        }
    }
    
    // Return a description to be used for the unpack task.
    private String getUnpackDescription() {
        String version = moduleVersion.getVersion()
        String targetName = moduleVersion.getName()
        if (shouldUnpackToCache()) {
            "Unpacks dependency '${targetName}' (version $version) to the cache."
        } else {
            "Unpacks dependency '${targetName}' to ${getTargetPathInWorkspace(null)}."
        }
    }
}


package holygradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.Task

class UnpackModuleVersion {
    public ModuleVersionIdentifier moduleVersion = null
    public boolean includeVersionNumberInPath = false
    public def artifacts = []
    private def dependencyRelativePaths = [:]
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
        
        // Read the relative paths for any of the dependencies. 
        def ivyXml = new XmlSlurper(false, false).parseText(ivyFile.text)
        ivyXml.dependencies.dependency.each { dependencyNode ->
            def relativePath = dependencyNode.@relativePath
            if (relativePath != null) {
                dependencyRelativePaths[
                    "${dependencyNode.@org}:${dependencyNode.@name}:${dependencyNode.@rev}"
                ] = relativePath
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
    
    public void addArtifacts(def arts) {
        for (art in arts) {
            artifacts.add(art)
        }
    }
    
    public String getFullCoordinate() {
        "${moduleVersion.getGroup()}:${moduleVersion.getName()}:${moduleVersion.getVersion()}"
    }
    
    // This method returns the relative path specified in the Ivy file from this module to 
    // the given module.
    public String getRelativePathForDependency(UnpackModuleVersion moduleVersion) {
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
        def packedDependency = getPackedDependency()
        if (packedDependency != null) {
            shouldApplyUpToDateChecks = packedDependency.shouldApplyUpToDateChecks()
        }
        
        def taskName = Helper.MakeCamelCase("extract", moduleVersion.getName()+moduleVersion.getVersion())
        def unpackTask = project.tasks.findByName(taskName)
        if (unpackTask == null) {
            // The task hasn't already been defined
            if (shouldApplyUpToDateChecks) {
                unpackTask = project.task(taskName, type: UnpackTask)
            } else {
                unpackTask = project.task(taskName, type: SpeedyUnpackTask)
            }
            unpackTask.initialize(project, this)
        }
        
        unpackTask
    }
    
    // Collect all parent, grand-parent, etc symlink tasks for creating symlinks in the workspace, pointing
    // to the central cache.
    public def collectParentSymlinkTasks(Project project) {
        def symlinkTasks = []
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
            def taskName = Helper.MakeCamelCase("symlink", moduleVersion.getName()+moduleVersion.getVersion())
            
            symlinkTask = project.tasks.findByName(taskName)
            if (symlinkTask == null) {
                def linkDir = getTargetPathInWorkspace(project)
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
        def targetDirName = getTargetDirName()       
        if (packedDependency00 != null) {
            // If we have a packed dependency then we can directly construct the target path.
            // We don't need to go looking through transitive dependencies.
            return new File(packedDependency00.getRelativePath(project), targetDirName) // FIXME
        } else {
            // If we don't return above then this must be a transitive dependency.
            // Therefore we must have a parent. Not having a parent is an error.
            if (parentUnpackModuleVersion == null) {
                def msg = "Error - module '${getFullCoordinate()}' has no parent module. "
                if (parent != null) {
                    msg += "(Project: ${project.name})"
                }
                throw new RuntimeException(msg)
            }
            
            def relativePathForDependency = parentUnpackModuleVersion.getRelativePathForDependency(this)
            if (relativePathForDependency == null ||
                relativePathForDependency.startsWith("/") || 
                relativePathForDependency.startsWith("\\")
            ) {
                // If there is no 'relativePath' or it begins with a slash then revert to the behaviour
                // of making the path relative to the root project.
                if (project == null) {
                    return new File(relativePathForDependency, targetDirName)
                } else {
                    return new File(project.rootProject.projectDir, relativePathForDependency + targetDirName)
                }
            } else {
                // Recursively navigate up the parent hierarchy, appending relative paths.
                return new File(parentUnpackModuleVersion.getTargetPathInWorkspace(project), relativePathForDependency + targetDirName)
            }
        }
    }
    
    public boolean shouldUnpackToCache() {
        getParentPackedDependency().shouldUnpackToCache()
    }
    
    // Return the location to which the artifacts will be unpacked. This could be to the global unpack 
    // cache or it could be to somewhere in the workspace.
    public File getUnpackDir(Project project) {
        if (shouldUnpackToCache()) {
            // Our closest packed-dependency entry (which could be for 'this' module, or any parent module)
            // dictated that we should unpack to the global cache.
            Helper.getGlobalUnpackCacheLocation(project, moduleVersion)
        } else {
            // We're unpacking directly into the workspace.
            getTargetPathInWorkspace(project)
        }
    }
    
    public String getUnpackDescription() {
        def version = moduleVersion.getVersion()
        def targetName = moduleVersion.getName()
        if (shouldUnpackToCache()) {
            "Unpacks dependency '${targetName}' (version $version) to the cache."
        } else {
            "Unpacks dependency '${targetName}' to ${getTargetPathInWorkspace(null)}."
        }
    }
}


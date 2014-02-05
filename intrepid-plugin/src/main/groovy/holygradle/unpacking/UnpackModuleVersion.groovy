package holygradle.unpacking

import holygradle.Helper
import holygradle.custom_gradle.util.CamelCase
import holygradle.dependencies.PackedDependencyHandler
import holygradle.symlinks.SymlinkTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import java.util.regex.Pattern

/** UnpackModuleVersion
 *
 *  See UnpackModule class description for overview of how UnpackModule/UnpackModuleVersion
 *  work together to represent the tree of packedDependencies/transitive dependencies, and
 *  produce corresponding tasks for performing the unpacking to the required place, and/or
 *  providing symlinks in the user's project in the required place.
 * 
 * An UnpackModuleVersion instance represents one version of a packed dependency.
 * The set of referencing 'parent' UnpackModuleVersions is included.  This is necessary
 * to correctly produce unpack tasks and symlink tasks, since the 'target location' of
 * the module depends on the 'target location' of parent modules, and similarly we may need
 * to produce symlink tasks to produce symlink from parent target location.
 *
 * Aggregated by UnpackModule (there's a collection of these for the project)
 * 
 */ 
class UnpackModuleVersion {
    public ModuleVersionIdentifier moduleVersion = null
    public boolean includeVersionNumberInPath = false
    public Map<ResolvedArtifact, Collection<String>> artifacts = [:] // a map from artifacts to sets of configurations that include the artifacts
    private Map<String, String> dependencyRelativePaths = [:]
    Set<UnpackModuleVersion> parentUnpackModuleVersions = new HashSet<UnpackModuleVersion>()
    
    private PackedDependencyHandler packedDependency00 = null
      

    public String ToString()
    {
        String result = "${getFullCoordinate()}"
        if (!parentUnpackModuleVersions.isEmpty()) {
            result += " required by { "
            parentUnpackModuleVersions.each { UnpackModuleVersion parent -> result += "${parent.getFullCoordinate()} " }
            result += "}"
        }
        return result
    }
    
    UnpackModuleVersion(
        ModuleVersionIdentifier moduleVersion,
        File ivyFile,
        PackedDependencyHandler packedDependency00
    ) {
        this.moduleVersion = moduleVersion
        
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
    
    public boolean shouldUnpackToCache() 
    {
        // default
        boolean shouldUnpackToCache = true

        if (packedDependency00 != null) {
            shouldUnpackToCache = packedDependency00.shouldUnpackToCache()
        }

        // If this or ANY parent specifies that this module should be unpacked in workspace rather than into the cache,
        // then we should assert all parents do:  It is possible for someone to write a script which specifies a packedDependency
        // module should be unpacked into the cache, but elsewhere (e.g. a different packedDependency or transitive dependency)
        // requests the module be unpacked directly into the workspace.  This is bad.  Throw a useful exception message
        UnpackModuleVersion anyUnpackToWorkspaceParent00 =  this.parentUnpackModuleVersions.find { it.shouldUnpackToCache() == false }
        if ((shouldUnpackToCache == false) || (anyUnpackToWorkspaceParent00 != null)) {
            if (this.parentUnpackModuleVersions.find { it.shouldUnpackToCache() } != null) {
                GString msg = "Inconsistent unpack policy (shouldUnpackToCache) specified for module ${this.ToString()}"
                throw new RuntimeException(msg)
            }
            shouldUnpackToCache = false
        } else {
            shouldUnpackToCache = true
        }

        return shouldUnpackToCache
    }

    public void addParent(UnpackModuleVersion parent)
    {
        parentUnpackModuleVersions.add(parent)        
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
    
    public Set<UnpackModuleVersion> getParents() {
        parentUnpackModuleVersions
    }
    
    /**
     * Returns a fully configured task for unpacking the module artifacts to the appropriate location, which also
     * implements the {@link Unpack} interface.
     * This could be to the central cache or directly to the workspace.
     *
     * @param project The project from which to get the unpack task.
     * @return A fully configured task for unpacking the module artifacts to the appropriate location, which also
     * implements the {@link Unpack} interface.
     */
    public Set<Task> getUnpackTasks(Project project) {
    
        Set<Task> result = new HashSet<Task>()
    
        boolean shouldApplyUpToDateChecks = false
        PackedDependencyHandler packedDependency = getPackedDependency()
        if (packedDependency != null) {
            shouldApplyUpToDateChecks = packedDependency.shouldApplyUpToDateChecks()
        }
        
       /**
        * If we are unpacking to cache, there will be only one target 'unpackDir'.  If we
        * are unpacking to workspace, then there may be multiple copies, depending on
        * their gradle project and subproject layout...
        */
        Set<File> unpackDirs = getUnpackDirs(project)

        unpackDirs.each { File unpackDir ->
       
            String taskName = CamelCase.build("extract", unpackDir.getAbsolutePath().replaceAll("[\\\\:]", ""))
            Task unpackTask = project.tasks.findByName(taskName)
            if (unpackTask == null) {
                
                project.logger.info("Creating task ${taskName}")
                // The task hasn't already been defined, so we should define it.
                final SevenZipHelper sevenZipHelper = new SevenZipHelper(project)
                if (!shouldApplyUpToDateChecks && sevenZipHelper.isUsable) {
                    unpackTask = project.task(taskName, type: SpeedyUnpackTask) { SpeedyUnpackTask task ->
                        task.initialize(
                            sevenZipHelper,
                            unpackDir,
                            getPackedDependency(),
                            artifacts.keySet()
                        )
                    }
                } else {
                    unpackTask = project.task(taskName, type: UnpackTask) { UnpackTask task ->
                        task.initialize(project, unpackDir, artifacts.keySet())
                    }
                }
                unpackTask.description = getUnpackDescription()
                result.add(unpackTask)
            }
        }
        
        return result
    }

    /**
     * Collect all parent, grand-parent, etc symlink tasks for creating symlinks in the workspace, pointing
     * to the central cache.
     */

    public Collection<Task> collectParentSymlinkTasks(Project project) {
        Collection<Task> symlinkTasks = []
        project.logger.info("->${getFullCoordinate()}.collectParentSymlinkTasks()")
        if (!parentUnpackModuleVersions.isEmpty()) {

            parentUnpackModuleVersions.each { UnpackModuleVersion parentUnpackModuleVersion ->
                symlinkTasks.addAll(parentUnpackModuleVersion.getSymlinkTasksIfUnpackingToCache(project))
            }
        }
        project.logger.info("<-${getFullCoordinate()}.collectParentSymlinkTasks() returning ${symlinkTasks}")
        return symlinkTasks
    }
    
    // This method returns a fully configured symlink task for creating a symlink in the workspace, pointing
    // to the central cache. The task will depend on any symlink tasks for parent, grand-parent modules.
    // Null will be returned if this module is not unpacked to the cache.
    public Collection<Task> getSymlinkTasksIfUnpackingToCache(Project project) {
        Collection<Task> symlinkTasks = []

        project.logger.info("-> ${getFullCoordinate()}.getSymlinkTasksIfUnpackingToCache()")
        if (this.shouldUnpackToCache()) {
        
            // If unpacking to cache, there should be only one unpack task and target location 
            // (i.e., to place the module into the cache)
            Set<File> unpackTargetDirs = getUnpackDirs(project)
            if (unpackTargetDirs.size() != 1) {
                throw new RuntimeException("Internal error processing unpack tasks for dependency ${getFullCoordinate()}")
            }
            
            File unpackTargetDir = unpackTargetDirs.iterator().next()
            
            Set<File> linkDirs = getTargetPathsInWorkspace(project)
            
            linkDirs.each { File linkDir ->
            
                String taskName = CamelCase.build("symlink", linkDir.getAbsolutePath().replaceAll("[\\\\:]", ""))
                
                Task symlinkTask = project.tasks.findByName(taskName)
                if (symlinkTask == null) {
                    project.logger.info("Creating task ${taskName}")
                    symlinkTask = project.task(taskName, type: SymlinkTask) {
                        group = "Dependencies"
                        description = "Build workspace-to-cache symlink for ${moduleVersion.getName()}:${moduleVersion.getVersion()}."
                    }
                    symlinkTask.configure(project, linkDir, unpackTargetDir)
                }
                
                symlinkTasks.add(symlinkTask)
                
                // Define dependencies from this symlink task to the symlink tasks for parent modules.
                // This ensures that the unpacking is done in a specific order, which is very useful because
                // it means that when 
                parentUnpackModuleVersions.each { UnpackModuleVersion parentUnpackModuleVersion ->
                    parentUnpackModuleVersion.collectParentSymlinkTasks(project).each {
                        symlinkTask.dependsOn it
                    }
                }
                
            }
        }
        project.logger.info("<- ${getFullCoordinate()}.getSymlinkTasksIfUnpackingToCache() returning ${symlinkTasks}")
        return symlinkTasks
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

    /**
     * Return the full path(s) of the directory(ies) that should be constructed in the workspace for the
     * unpacked artifacts. Depending on config. in the parent/grand-parent 'packedDependency', this path could be
     * used for generating a symlink, or it could be a 'real' target directory to unpack into
     *
     * A dependency can appear more than once within a project, and it is possible for it to be required
     * in more than one location, e.g., a transitive dependency of one or more modules, as well as being
     * a 1st-level 'packedDependency' specified in a gradle script.  Therefore this method must
     * return a Set<File>
     **/
    public Set<File> getTargetPathsInWorkspace(Project project) {
        Set<File> result = new HashSet<File>()


        if (parentUnpackModuleVersions.isEmpty() && (packedDependency00 == null)) {
            // If this module has no parent, then it isn't a transitive dependency, i.e. we must have
            // packedDependency declaration associated with it from the script
            GString msg = "Error - module '${getFullCoordinate()}' has no parent module.  (Project: ${project.name})"
            throw new RuntimeException(msg)
        }

        // deal with any parent modules
        parentUnpackModuleVersions.each { UnpackModuleVersion parentUnpackModuleVersion ->

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
                    result.add(new File(relativePathForDependency))
                } else {
                    result.add(new File(project.rootProject.projectDir, relativePathForDependency))
                }
            } else {
                // Recursively navigate up the parent hierarchy, appending relative paths.
                Set<File> parentResult = parentUnpackModuleVersion.getTargetPathsInWorkspace(project)
                parentResult.collect( result, { File f -> new File(f, relativePathForDependency) } )
            }
        }

        if (packedDependency00 != null) {
            // This is a first-level 'packedDependency'.  But note that it may also be a transitive dependency
            // of another module.  The logic here allows for this as part of fix for defect GR #3723
            String targetPath = packedDependency00.getFullTargetPathWithVersionNumber(moduleVersion.getVersion())
            if (project == null) {
                result.add(new File(targetPath))
            } else {
                result.add(new File(project.projectDir, targetPath))
            }
        }

        return result
    }
        
    // Return the location to which the artifacts will be unpacked. This could be to the global unpack 
    // cache or it could be to somewhere in the workspace.
    private Set<File> getUnpackDirs(Project project) {
        Set<File> result = new HashSet<File>()
        if (this.shouldUnpackToCache()) {
            // Our closest packed-dependency entry (which could be for 'this' module, or any parent module)
            // dictated that we should unpack to the global cache.
            result.add(Helper.getGlobalUnpackCacheLocation(project, moduleVersion))
        } else {
            // We're unpacking directly into the workspace - this may mean duplicates,
            // so we have to deal with that 
            result.addAll(getTargetPathsInWorkspace(project))
        }
        return result
    }
    
    // Return a description to be used for the unpack task.
    private String getUnpackDescription() {
        String version = moduleVersion.getVersion()
        String targetName = moduleVersion.getName()
        if (this.shouldUnpackToCache()) {
            "Unpacks dependency '${targetName}' (version $version) to the cache."
        } else {
            "Unpacks dependency '${targetName}' to ${getTargetPathsInWorkspace(null)}."
        }
    }
}


package holygradle.unpacking

import holygradle.Helper
import holygradle.custom_gradle.util.CamelCase
import holygradle.dependencies.PackedDependencyHandler
import holygradle.symlinks.SymlinksToCacheTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact

class UnpackModuleVersion {
    public final ModuleVersionIdentifier moduleVersion = null
    public final boolean includeVersionNumberInPath = false
    // A map from artifacts to sets of configurations that include the artifacts.
    public final Map<ResolvedArtifact, Set<String>> artifacts = [:].withDefault { new HashSet<String>() }
    // The set of configurations in the containing project which lead to this module being included.
    public final Set<String> originalConfigurations = new HashSet<String>()
    private final Map<String, String> dependencyRelativePaths = [:]
    private UnpackModuleVersion parentUnpackModuleVersion
    private PackedDependencyHandler packedDependency00 = null
    
    UnpackModuleVersion(
        ModuleVersionIdentifier moduleVersion,
        File ivyFile,
        UnpackModuleVersion parentUnpackModuleVersion,
        PackedDependencyHandler packedDependency00
    ) {
        // Therefore we must have a parent. Not having a parent is an error.
        if (packedDependency00 == null && parentUnpackModuleVersion == null) {
            throw new RuntimeException("Module '${moduleVersion}' has no parent module.")
        }

        this.moduleVersion = moduleVersion
        this.parentUnpackModuleVersion = parentUnpackModuleVersion
        
        this.packedDependency00 = packedDependency00
        if (packedDependency00 != null) {
            this.includeVersionNumberInPath = packedDependency00.pathIncludesVersionNumber()
        }
        
        // Read the relative paths for any of the dependencies.  (No point trying to use static types here as we're
        // using GPath, so the returned objects have magic properties.)
        def ivyXml = new XmlSlurper(false, false).parseText(ivyFile.text)
        ivyXml.dependencies.dependency.each { dep ->
            def relativePath = dep.@relativePath?.toString()
            if (relativePath != null) {
                final String moduleVersionId = "${dep.@org}:${dep.@name}:${dep.@rev}"
                // We've occasionally seen hand-crafted ivy.xml files which have a trailing slash on the relativePath,
                // which leads to us creating symlinks one level down from where we want them; so, strip it if present.
                if (relativePath.endsWith('/')) {
                    relativePath = relativePath[0..-2]
                }
                dependencyRelativePaths[moduleVersionId] = relativePath
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
    
    public void addArtifacts(Iterable<ResolvedArtifact> arts, String originalConf) {
        for (art in arts) {
            artifacts[art].add(originalConf)
        }
        originalConfigurations.add(originalConf)
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

    /**
     * Returns a fully configured task for unpacking the module artifacts to the appropriate location, which also
     * implements the {@link Unpack} interface.
     * This could be to the central cache or directly to the workspace.
     *
     * @param project The project from which to get the unpack task.
     * @return A fully configured task for unpacking the module artifacts to the appropriate location, which also
     * implements the {@link Unpack} interface.
     */
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
            final SevenZipHelper sevenZipHelper = new SevenZipHelper(project)
            if (!shouldApplyUpToDateChecks && sevenZipHelper.isUsable) {
                unpackTask = project.task(taskName, type: SpeedyUnpackTask) { SpeedyUnpackTask task ->
                    task.initialize(
                        sevenZipHelper,
                        getUnpackDir(project),
                        getPackedDependency(),
                        artifacts.keySet()
                    )
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

    /**
     * This method configures the {@code symlinksToCacheTask} to create a symlink to the version of this module in the
     * cache, provided that the relevant {@code packedDependencies} entry has {@code unpackToCache = true} and has not
     * had {@code noCreateSymlinkToCache() called}; otherwise, it does not change the task's configuration.
     * @param symlinksToCacheTask
     * @return true if this version was added to the task, otherwise false
     */
    public void addToSymlinkTaskIfRequired(SymlinksToCacheTask symlinksToCacheTask) {
        if (shouldCreateSymlinkToCache()) {
            symlinksToCacheTask.addUnpackModuleVersion(this)
        }
    }
    
    // This returns the packedDependencies entry which configures some aspects of this module.
    // The entry could be specifically for this module, in the case where a project directly
    // specifies a dependency. Or this entry could be for a 'parent' packedDependency entry 
    // i.e. one which causes this module to be pulled in as a transitive dependency.
    public PackedDependencyHandler getSelfOrAncestorPackedDependency() {
        if (packedDependency00 != null) {
            return packedDependency00
        }
        
        // If we don't return packedDependency00 above then this must be a transitive dependency.
        // Therefore we must have a parent. Not having a parent is an error.
        if (parentUnpackModuleVersion == null) {
            throw new RuntimeException("Error - module '${getFullCoordinate()}' has no parent module.")
        }
        return parentUnpackModuleVersion.getSelfOrAncestorPackedDependency()
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

            String relativePathForDependency = parentUnpackModuleVersion.getRelativePathForDependency(this)
            if (relativePathForDependency == "") {
                // If the relative path is empty we need to supply the name of the target directory ourselves.
                relativePathForDependency = getTargetDirName()
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
        getSelfOrAncestorPackedDependency().shouldUnpackToCache()
    }

    // If true, a symlink for this module should be created, to the central cache, otherwise no symlink is created.
    private boolean shouldCreateSymlinkToCache() {
        getSelfOrAncestorPackedDependency().shouldCreateSymlinkToCache()
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


    @Override
    public String toString() {
        return "UnpackModuleVersion{" +
            "moduleVersion=" + moduleVersion +
            ", packedDependency target path=" + (packedDependency00 == null ?
                "n/a" :
                packedDependency00.getFullTargetPathWithVersionNumber(moduleVersion.getVersion())
            ) +
            ", parent relative path=" + (packedDependency00 == null ?
                parentUnpackModuleVersion?.getRelativePathForDependency(this) :
                "n/a"
            ) +
            ", parentUnpackModuleVersion=" + parentUnpackModuleVersion?.moduleVersion +
            '}';
    }
}


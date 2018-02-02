package holygradle.unpacking

import holygradle.Helper
import holygradle.dependencies.PackedDependencyHandler

import holygradle.dependencies.SourceOverrideHandler
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact

class UnpackModuleVersion {
    public final Project project
    public final ModuleVersionIdentifier moduleVersion
    public boolean includeVersionNumberInPath
    // A map from artifacts to sets of configurations that include the artifacts.
    public final Map<ResolvedArtifact, Set<String>> artifacts = [:].withDefault { new HashSet<String>() }
    // The set of configurations in the containing project which lead to this module being included.
    public final Set<String> originalConfigurations = new HashSet<String>()
    private PackedDependencyHandler packedDependency00 = null
    private Set<UnpackModuleVersion> parents = new HashSet<>()

    UnpackModuleVersion(
        Project project,
        ModuleVersionIdentifier moduleVersion,
        Set<UnpackModuleVersion> parents,
        PackedDependencyHandler packedDependency00
    ) {
        this.project = project
        this.moduleVersion = moduleVersion
        this.parents = parents
        this.packedDependency00 = packedDependency00
        if (packedDependency00 == null) {
            this.includeVersionNumberInPath = false
        } else {
            this.includeVersionNumberInPath = packedDependency00.pathIncludesVersionNumber()
        }

        // Therefore we must have a parent. Not having a parent is an error.
        if (packedDependency00 == null && this.parents.size() == 0) {
            throw new RuntimeException("Module '${moduleVersion}' has no parent module.")
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

    public boolean hasArtifacts() {
        !artifacts.keySet().empty
    }

    public String getFullCoordinate() {
        "${moduleVersion.getGroup()}:${moduleVersion.getName()}:${moduleVersion.getVersion()}"
    }

    public void addParents(Collection<UnpackModuleVersion> parents) {
        this.parents.addAll(parents)
    }
    
    // This returns the packedDependencies entry corresponding to this dependency. This will
    // return null if no such entry exists, which would be the case if this is a transitive
    // dependency.
    public PackedDependencyHandler getPackedDependency() {
        packedDependency00
    }

    void setPackedDependency(PackedDependencyHandler packedDependency) {
        packedDependency00 = packedDependency
    }

    // TODO: will need to track all parents because each one (unpacked to a different location in
    // the central cache) will need to create a link to this module in the cache.
    public Collection<UnpackModuleVersion> getParents() {
        parents
    }

    /**
     * Returns an {@link UnpackEntry} object which describes how to unpack this module version in the context of the
     * given {@code project}.  This is suitable for passing to
     * {@link SpeedyUnpackManyTask#addEntry(ModuleVersionIdentifier,UnpackEntry)}
     * @param project
     * @return An {@link UnpackEntry} object which describes how to unpack this module version
     */
    public UnpackEntry getUnpackEntry() {
        return new UnpackEntry(
            artifacts.keySet()*.file,
            getUnpackDir(),
            (boolean)getPackedDependency()?.shouldApplyUpToDateChecks(),
            (boolean)getPackedDependency()?.shouldMakeReadonly()
        )
    }

    // This returns the packedDependencies entry which configures some aspects of this module.
    // The entry could be specifically for this module, in the case where a project directly
    // specifies a dependency. Or this entry could be for a 'parent' packedDependency entry 
    // i.e. one which causes this module to be pulled in as a transitive dependency.
    public Collection<PackedDependencyHandler> getSelfOrAncestorPackedDependencies() {
        if (packedDependency00 != null) {
            return [packedDependency00]
        }

        // If we don't return packedDependency00 above then this must be a transitive dependency.
        // Therefore we must have a parent. Not having a parent is an error.
        if (parents.size() == 0) {
            throw new RuntimeException("Error - module '${getFullCoordinate()}' has no parent module.")
        }

        return parents.collectMany { p -> p.getSelfOrAncestorPackedDependencies()}
    }

    private <TSource, TValue> TValue getUniqueValue(
        String methodName,
        Collection<TSource> sources,
        Closure<TValue> getValue
    ) {
        Collection<TValue> values = sources.collect(getValue)
        Collection<TValue> uniqueValues = values.unique()

        // If there is not exactly one result, throw an error
        if (uniqueValues.size() != 1) {
            throw new RuntimeException("Error - module '${getFullCoordinate()}' has different parent results for ${methodName}: ${values}")
        }
        return uniqueValues.first()
    }

    private String getParentRelativePath(UnpackModuleVersion module) {
        getUniqueValue("getParentRelativePath", parents) { UnpackModuleVersion it ->
            it.getRelativePathForDependency(module)
        }
    }

    private String getParentTargetPath(Project project) {
        getUniqueValue("getParentTargetPath", parents) { UnpackModuleVersion it ->
            it.getTargetPathInWorkspace()
        }.toString()
    }

    private String getParentModuleVersion() {
        getUniqueValue("getParentModuleVersion", parents) { UnpackModuleVersion it ->
            it.moduleVersion
        }.toString()
    }

    private String getParentModuleVersions() {
        parents.collect {
            it.moduleVersion
        }.join(", ")
    }

    // Return the name of the directory that should be constructed in the workspace for the unpacked artifacts. 
    // Depending on some other configuration, this directory name could be used for a link or a real directory.
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
    // Depending on some other configuration, this path could be used for a link or a real directory.
    public File getTargetPathInWorkspace() {
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
            // Recursively navigate up the parent hierarchy, appending relative paths.
            return new File(
                parentUnpackModuleVersion.targetPathInWorkspace.parentFile,
                targetDirName
            )
        }
    }

    // If true this module should be unpacked to the central cache, otherwise it should be unpacked
    // directly to the workspace.
    private boolean shouldUnpackToCache() {
        getUniqueValue("shouldUnpackToCache", getSelfOrAncestorPackedDependencies()) { PackedDependencyHandler it ->
            it.shouldUnpackToCache()
        }.booleanValue()
    }

    /**
     * If true, a link for this module should be created, to the central cache, otherwise no link should be created.
     * A link should be created if
     * <ul>
     *     <li>the relevant {@code packedDependencies} entry has {@code unpackToCache = true} (the default);</li>
     *     <li>that entry has not had {@code noCreateSymlinkToCache() called on it; and</li>
     *     <li>this module version actually has any artifacts -- if not, nothing will be unpacked in the cache, so there
     *     will be no folder to which to make a symlink.</li>
     * </ul>
     * @return A flag indicating whether to create a symlink to the unpack cache for this version.
     */
    public boolean shouldCreateLinkToCache() {
        getUniqueValue("shouldCreateLinkToCache", getSelfOrAncestorPackedDependencies()) { PackedDependencyHandler it ->
            it.shouldCreateLinkToCache()
        }.booleanValue() && hasArtifacts()
    }

    public File getLinkDir(Project project) {
        def sourceOverrideHandler = project.rootProject.sourceOverrides.find { SourceOverrideHandler handler ->
            handler.dummyDependencyCoordinate == fullCoordinate
        }

        if (sourceOverrideHandler != null) {
            new File(sourceOverrideHandler.from)
        } else {
            getUnpackDir()
        }
    }


    // Return the location to which the artifacts will be unpacked. This could be to the global unpack 
    // cache or it could be to somewhere in the workspace.
    public File getUnpackDir() {
        if (shouldUnpackToCache()) {
            // Our closest packed-dependency entry (which could be for 'this' module, or any parent module)
            // dictated that we should unpack to the global cache.
            Helper.getGlobalUnpackCacheLocation(project, moduleVersion)
        } else {
            // We're unpacking directly into the workspace.
            targetPathInWorkspace
        }
    }

    @Override
    public String toString() {
        return "UnpackModuleVersion{" +
            "moduleVersion=" + moduleVersion +
            ", packedDependency target path='" + (packedDependency00 == null ?
                "n/a" :
                packedDependency00.getFullTargetPathWithVersionNumber(moduleVersion.getVersion())
            ) +
            "', parentUnpackModuleVersion={" + getParentModuleVersions() +
            '}';
    }
}


package holygradle.unpacking

import holygradle.dependencies.PackedDependencyHandler
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

/**
 * This project extension provides information about the state of packed dependencies when they are resolved.
 */
class PackedDependenciesStateHandler {
    /**
     * This class combines a module version ID and configuration name into one immutable, equatable object.
     *
     * When visiting configurations in the original project's dependency graph (as opposed to in the detached
     * configurations for ivy.xml files), we need to track not just which "module versions" we've seen, but which
     * "module version plus module configuration", since dependencies differ per configuration.
     */
    private static class ResolvedDependencyId
        extends AbstractMap.SimpleImmutableEntry<ModuleVersionIdentifier, String>
    {
        /**
         *  Creates an entry representing a resolved module version, visited under a particular configuration.
         *
         * @param key the key represented by this entry
         * @param value the value represented by this entry
         */
        ResolvedDependencyId(ModuleVersionIdentifier versionId, String configuration) {
            super(versionId, configuration)
        }

        public ModuleVersionIdentifier getId() { super.getKey() }
        public String getConfiguration() { super.getValue() }
    }

    /**
     * A class encapsulating choices about visiting nodes in a graph of {@link ResolvedDependency} instances.
     *
     * Instances of this class are used, rather than separate boolean flags, so that some of the calculation for the
     * decisions for both node and children can be made together, on the basis of the same state.
     */
    private static class VisitChoice {
        /**
         * A flag indicating whether to call a {@code dependencyAction} on a {@link ResolvedDependency} itself..
         */
        public final boolean visitDependency
        /**
         * A value indicating whether to call a {@code dependencyAction} on the children of a {@link ResolvedDependency}.
         */
        public final boolean visitChildren

        /**
         * Creates an instance of {@link VisitChoice}.
         * @param visitDependency A flag indicating whether to call a {@code dependencyAction} on a
         * {@link ResolvedDependency} itself.
         * @param visitChildren A value indicating whether to call a {@code dependencyAction} on the children of a
         * {@link ResolvedDependency}.
         */
        VisitChoice(boolean visitDependency, boolean visitChildren) {
            this.visitDependency = visitDependency
            this.visitChildren = visitChildren
        }
    }

    // Map of "original configuration name" -> "original module ID" -> ivy file location
    private Map<String, Map<ModuleVersionIdentifier, File>> ivyFileMaps = new HashMap()

    private final Project project
    private Map<ModuleIdentifier, UnpackModule> unpackModulesMap = null
    private Collection<UnpackModule> unpackModules = null

    public static PackedDependenciesStateHandler createExtension(Project project) {
        project.extensions.packedDependenciesState = new PackedDependenciesStateHandler(project)
        project.extensions.packedDependenciesState
    }

    /**
     * Creates an instance of {@link PackedDependenciesStateHandler} for the given project.
     *
     * @param project The project to which this extension instance applies.
     */
    public PackedDependenciesStateHandler(Project project) {
        this.project = project
    }

    /**
     * Calls a closure for all {@link ResolvedDependency} instances in the transitive graph, selecting whether or not
     * to visit the children of eah using a predicate.  The same dependency may be visited more than once, if it appears
     * in the graph more than once.
     *
     * @param dependencies The initial set of dependencies.
     * @param dependencyAction The closure to call for each {@link ResolvedDependency}.
     * @param visitDependencyPredicate A predicate closure to call for {@link ResolvedDependency} to decide whether to
     * call the dependencyAction for the dependency.
     * @param visitChildrenPredicate A predicate closure to call for {@link ResolvedDependency} to decide whether to
     * visit its children.  The children may be visited even if the visitDependencyPredicate returned false.
     */
    private static void traverseResolvedDependencies(
        Set<ResolvedDependency> dependencies,
        Stack<ResolvedDependency> dependencyStack,
        Closure dependencyAction,
        Closure getVisitChoice
    ) {
        // Note: This method used to have the predicates as optional arguments, where null meant "always true", but I
        // kept making mistakes with them, so clearly it was a bad idea.
        dependencies.each { resolvedDependency ->
            try {
                dependencyStack.push(resolvedDependency)
                //println("tRD: ${dependencyStack.join(' <- ')}")

                VisitChoice visitChoice = getVisitChoice(resolvedDependency)

                if (visitChoice.visitDependency) {
                    dependencyAction(resolvedDependency)
                }

                if (visitChoice.visitChildren) {
                    traverseResolvedDependencies(
                        resolvedDependency.children,
                        dependencyStack,
                        dependencyAction,
                        getVisitChoice
                    )
                }
            } finally {
                dependencyStack.pop()
            }
        }
    }

    /**
     * Calls a closure for all {@link ResolvedDependency} instances in the transitive graph, selecting whether or not
     * to visit the children of eah using a predicate.  The same dependency may be visited more than once, if it appears
     * in the graph more than once.
     *
     * @param dependencies The initial set of dependencies.
     * @param dependencyAction The closure to call for each {@link ResolvedDependency}.
     * @param visitDependencyPredicate A predicate closure to call for {@link ResolvedDependency} to decide whether to
     * call the dependencyAction for the dependency.
     * @param visitChildrenPredicate A predicate closure to call for {@link ResolvedDependency} to decide whether to
     * visit its children.  The children may be visited even if the visitDependencyPredicate returned false.
     */
    private static void traverseResolvedDependencies(
        Set<ResolvedDependency> dependencies,
        Closure dependencyAction,
        Closure getVisitChoice
    ) {
        // Note: This method used to have the predicates as optional arguments, where null meant "always true", but I
        // kept making mistakes with them, so clearly it was a bad idea.
        traverseResolvedDependencies(
            dependencies,
            new Stack<ResolvedDependency>(),
            dependencyAction,
            getVisitChoice
        )
    }

    private boolean isModuleInBuild(ModuleVersionIdentifier id) {
        return project.rootProject.allprojects.find {
            (it.group == id.group) &&
                (it.name == id.name) &&
                (it.version == id.version)
        } != null
    }

    /**
     * Given a set of resolved dependencies, returns a collection of unresolved dependencies for the {@code ivy.xml}
     * files corresponding to the resolved modules.  This allows us to find those files on disk (to read custom parts of
     * the XML), even though Gradle doesn't normally expose their location.
     *
     * @param dependencies A set of resolved dependencies.
     * @return A set of unresolved dependencies for the corresponding {@code ivy.xml} files.
     */
    private Collection<Dependency> getDependenciesForIvyFiles(
        Set<ResolvedDependency> dependencies
    ) {
        Map<ModuleVersionIdentifier, Dependency> result = [:]
        Set<ResolvedDependencyId> dependencyConfigurationsAlreadySeen = new HashSet<ResolvedDependencyId>()

        traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id

                ExternalModuleDependency dep = new DefaultExternalModuleDependency(
                    id.group, id.name, id.version, resolvedDependency.configuration
                )
                dep.artifact { art ->
                    art.name = "ivy"
                    art.type = "ivy"
                    art.extension = "xml"
                }
                result[id] = dep
                project.logger.debug "getDependenciesForIvyFiles: Added ${dep} for ${id}"
            },
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                final boolean dependencyAlreadySeen = result.containsKey(id)
                project.logger.debug "getDependenciesForIvyFiles: Considering dep ${id}: dependencyAlreadySeen == ${dependencyAlreadySeen}"
                final boolean moduleIsInBuild = isModuleInBuild(id)
                project.logger.debug "getDependenciesForIvyFiles: Considering dep ${id}: moduleIsInBuild == ${moduleIsInBuild}"
                final boolean dependencyConfigurationAlreadySeen = !dependencyConfigurationsAlreadySeen.add(
                    new ResolvedDependencyId(id, resolvedDependency.configuration)
                )
                project.logger.debug "getDependenciesForIvyFiles: Considering dep ${id}: dependencyConfigurationAlreadySeen == ${dependencyConfigurationAlreadySeen}"
                return new VisitChoice(
                    // Visit this dependency only if we've haven't seen it already, and it's not a module we're building from
                    // source (because we don't need an ivy file for that -- we have relative path info in the source dep).
                    !dependencyAlreadySeen && !moduleIsInBuild,
                    // Visit this dependency;s children only if we've haven't seen it already.  We still want to search into
                    // modules which are part of the source for this build, so we don't call isModuleInBuild.
                    !dependencyConfigurationAlreadySeen
                )
            }
        )

        return new ArrayList(result.values())
    }

    /**
     * Populates a map from module version ID to the file location (in the local Gradle cache) of the corresponding
     * {@code ivy.xml} file.
     *
     * @param dependencies A set of resolved dependencies on {@code ivy.xml} files.
     * @return The map from IDs to files.
     */
    private Map<ModuleVersionIdentifier, File> getResolvedIvyFiles(
        Set<ResolvedDependency> dependencies
    ) {
        Map<ModuleVersionIdentifier, File> ivyFiles = new HashMap<ModuleVersionIdentifier, File>()
        // Don't care about tracking resolved dependency configurations here, because we're resolving the single
        // configuration of the extra graph of dependencies on ivy.xml files which this class constructs, and that is
        // flat and doesn't use dependencies on multiple configurations.

        traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                final ModuleVersionIdentifier id = resolvedDependency.module.id
                final ResolvedArtifact art = resolvedDependency.moduleArtifacts.find { a -> a.name == "ivy" }
                final File file = art?.file
                ivyFiles[id] = file
                project.logger.debug "getResolvedIvyFiles: Added ${file} for ${id}"
            },
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                final boolean alreadySeen = ivyFiles.containsKey(id)
                project.logger.debug "getResolvedIvyFiles: Considering dep ${id}: alreadySeen == ${alreadySeen}"
                project.logger.debug "getResolvedIvyFiles: Considering children of dep ${id}: alreadySeen == ${alreadySeen}"
                return new VisitChoice(
                    // Visit this dependency only if we haven't seen it already.
                    !alreadySeen,
                    // Visit this dependency's children only if we haven't seen it already.
                    !alreadySeen
                )
            }
        )
        return ivyFiles
    }

    // Get the ivy files for a given configuration by
    //  (a) finding all the resolved modules for the configuration;
    //  (b) making a matching collection of unresolved dependencies with the same module versions, but with the ivy.xml
    // file as the only artifact;
    //  (c) resolving the latter collection and pulling out the artifact locations.
    //
    // We can't just ask Gradle for the locations, because it doesn't expose them.  See the forum post at
    // http://forums.gradle.org/gradle/topics/how_to_get_hold_of_ivy_xml_file_of_dependency
    private Map<ModuleVersionIdentifier, File> getIvyFilesForConfiguration(Configuration conf) {
        project.logger.debug "getIvyFilesForConfiguration(${conf})"

        Map<ModuleVersionIdentifier, File> ivyFiles = ivyFileMaps[conf.name]
        if (ivyFiles == null) {
            Collection<Dependency> ivyDeps = getDependenciesForIvyFiles(
                conf.resolvedConfiguration.firstLevelModuleDependencies
            )
            //noinspection GroovyAssignabilityCheck
            Configuration ivyConf = project.configurations.detachedConfiguration(* ivyDeps)
            ivyFiles = getResolvedIvyFiles(ivyConf.resolvedConfiguration.firstLevelModuleDependencies)
            ivyFileMaps[conf.name] = ivyFiles
            project.logger.debug("Resolved ivy files for ${conf}: ${ivyFiles.entrySet().join('\r\n')}")
        }
        return ivyFiles
    }

    // Get the ivy file for the resolved dependency.  This may either be in the
    // gradle cache, or exist locally in "localArtifacts" (which we create for
    // those who may not have access to the artifact repository).  May return null.
    private File getIvyFile(
        Configuration conf,
        ResolvedDependency resolvedDependency
    ) {
        Map<ModuleVersionIdentifier, File> ivyFiles = getIvyFilesForConfiguration(conf)
        return ivyFiles[resolvedDependency.module.id]
    }

    private void collectUnpackModules(
        Configuration originalConf,
        Collection<PackedDependencyHandler> packedDependencies,
        Map<ModuleIdentifier, UnpackModule> unpackModules,
        Collection<ModuleVersionIdentifier> modulesWithoutIvyFiles,
        Set<ResolvedDependency> dependencies
    ) {
        project.logger.debug("collectUnpackModules for ${originalConf}")
        project.logger.debug("    starting with unpackModules ${unpackModules.keySet().sort().join('\r\n')}")
        project.logger.debug("    and packedDependencies ${packedDependencies*.name.sort().join('\r\n')}")

        Set<ResolvedDependencyId> dependencyConfigurationsAlreadySeen = new HashSet<ResolvedDependencyId>()

        traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                // If we get here then we found an ivy file for the dependency, and we will just assume that we should
                // be unpacking it.  Ideally we should look for a custom XML tag that indicates that it was published
                // by the intrepid plugin. However, that would break compatibility with lots of artifacts that have
                // already been published.
                project.logger.debug("collectUnpackModules: processing ${id}")

                // Find or create an UnpackModule instance.
                UnpackModule unpackModule = unpackModules[id.module]
                if (unpackModule == null) {
                    unpackModule = new UnpackModule(id.group, id.name)
                    unpackModules[id.module] = unpackModule
                    project.logger.debug("collectUnpackModules: created module for ${id.module}")
                }

                // Find a parent UnpackModuleVersion instance i.e. one which has a dependency on
                // 'this' UnpackModuleVersion. There will only be a parent if this is a transitive
                // dependency.
                //
                // TODO: There could be more than one parent. Deal with it gracefully.  We might need to drop the "have
                // seen it before" filter from the visitDependencyPredicate.
                UnpackModuleVersion parentUnpackModuleVersion =
                    resolvedDependency.getParents().findResult { parentDependency ->
                        ModuleVersionIdentifier parentDependencyVersion = parentDependency.module.id
                        UnpackModule parentUnpackModule = unpackModules[parentDependencyVersion.module]
                        parentUnpackModule?.getVersion(parentDependencyVersion)
                    }

                // Find or create an UnpackModuleVersion instance.
                UnpackModuleVersion unpackModuleVersion
                if (unpackModule.versions.containsKey(id.version)) {
                    unpackModuleVersion = unpackModule.versions[id.version]
                } else {

                    // If this resolved dependency is a transitive dependency, "thisPackedDep" will be null.
                    PackedDependencyHandler thisPackedDep = packedDependencies.find {
                        // Note that we don't compare the version, because we might have resolved to another version if
                        // multiple versions were requested in the dependency graph.  In that case, we still want to
                        // regard this packed dependency as mapping to the given UnpackModuleVersion, i.e., to the
                        // resolved dependency version.
                        return (it.groupName == id.group) &&
                            (it.dependencyName == id.name)
                    }

                    File ivyFile = getIvyFile(originalConf, resolvedDependency)
                    project.logger.debug "collectUnpackModules: Ivy file under ${originalConf} for ${id} is ${ivyFile}"
                    unpackModuleVersion = new UnpackModuleVersion(id, ivyFile, parentUnpackModuleVersion, thisPackedDep)
                    unpackModule.versions[id.version] = unpackModuleVersion
                    project.logger.debug(
                        "collectUnpackModules: created version for ${id} " +
                        "(pUMV = ${parentUnpackModuleVersion}, tPD = ${thisPackedDep?.name})"
                    )

                }

                project.logger.debug("collectUnpackModules: adding ${id.module} originalConf ${originalConf.name} artifacts ${resolvedDependency.moduleArtifacts}")
                unpackModuleVersion.addArtifacts(resolvedDependency.getModuleArtifacts(), originalConf.name)
            },
            { ResolvedDependency resolvedDependency ->
                // Visit this dependency only if: we've haven't seen it already for the configuration we're
                // processing, it's not a module we're building from source (because we don't want to include those
                // as UnpackModules), and we can find its ivy.xml file (because we need that for relativePath to
                // other modules).
                //
                // Visit this dependency's children only if: we've haven't seen it already for the configuration
                // we're processing, and we can find its ivy.xml file (because we need that for relativePath to
                // other modules).

                ModuleVersionIdentifier id = resolvedDependency.module.id

                final ResolvedDependencyId newId = new ResolvedDependencyId(id, resolvedDependency.configuration)
                project.logger.debug("Adding ${newId} to ${dependencyConfigurationsAlreadySeen}")
                final boolean isNewModuleConfiguration = dependencyConfigurationsAlreadySeen.add(newId)
                if (!isNewModuleConfiguration) {
                    project.logger.debug(
                        "collectUnpackModules: Skipping ${id}, conf ${resolvedDependency.configuration}, " +
                        "because it has already been visited"
                    )
                }

                final boolean moduleIsInBuild = isModuleInBuild(id)
                if (moduleIsInBuild) {
                    project.logger.debug("collectUnpackModules: Skipping ${id} because it is part of this build")
                }

                final boolean isNewNonBuildModuleConfiguration = isNewModuleConfiguration && !moduleIsInBuild

                // Is there an ivy file corresponding to this dependency?
                File ivyFile = getIvyFile(originalConf, resolvedDependency)
                final boolean ivyFileExists = ivyFile?.exists()
                // If this is a new module, not part of the build, but we can't find its ivy file, then track it so
                // we can throw an exception later (after we've checked all dependencies).
                if (isNewNonBuildModuleConfiguration && !ivyFileExists) {
                    if (ivyFile == null) {
                        project.logger.error("collectUnpackModules: Failed to find location of ivy.xml file for ${id}")
                    } else {
                        project.logger.error("collectUnpackModules: ivy.xml file for ${id} not found at ${ivyFile}")
                    }
                    modulesWithoutIvyFiles.add(id)
                }

                if (ivyFileExists) {
                    project.logger.debug "collectUnpackModules: Ivy file under ${originalConf} for ${id} is ${ivyFile}"
                }
                return new VisitChoice(
                    isNewNonBuildModuleConfiguration && ivyFileExists,
                    isNewModuleConfiguration && ivyFileExists
                )
            }
        )
    }

    /**
     * Returns a collection of objects representing the transitive set of unpacked modules used by the project.
     *
     * @return The transitive set of unpacked modules used by the project.
     */
    public getAllUnpackModules() {
        if (unpackModulesMap == null) {
            project.logger.debug("getAllUnpackModules for ${project}")

            final packedDependencies = project.packedDependencies as Collection<PackedDependencyHandler>
            // Build a list (without duplicates) of all artifacts the project depends on.
            unpackModulesMap = [:]
            Collection<ModuleVersionIdentifier> modulesWithoutIvyFiles = new HashSet<ModuleVersionIdentifier>()
            project.configurations.each { conf ->
                ResolvedConfiguration resConf = conf.resolvedConfiguration
                collectUnpackModules(
                    conf,
                    packedDependencies,
                    unpackModulesMap,
                    modulesWithoutIvyFiles,
                    resConf.getFirstLevelModuleDependencies()
                )
            }

            if (!modulesWithoutIvyFiles.isEmpty()) {
                throw new RuntimeException("Some dependencies had no ivy.xml file")
            }

            // Check if we have artifacts for each entry in packedDependency.
            if (!project.gradle.startParameter.isOffline()) {
                boolean fail = false
                packedDependencies.each { dep ->
                    ModuleIdentifier depId = new DefaultModuleIdentifier(dep.groupName, dep.dependencyName)
                    if (!unpackModulesMap.containsKey(depId)) {
                        project.logger.error(
                            "No artifacts detected for dependency '${dep.name}'. " +
                            "Check that you have correctly defined the configurations."
                        )
                        fail = true
                    }
                }
                if (fail) {
                    throw new RuntimeException("Some dependencies had no artifacts")
                }
            }

            // Check if we need to force the version number to be included in the path in order to prevent
            // two different versions of a module to be unpacked to the same location.
            Map<File, Collection<UnpackModuleVersion>> targetLocations = [:].withDefault { new ArrayList<UnpackModuleVersion>() }
            unpackModulesMap.values().each { UnpackModule module ->
                module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                    File targetPath = versionInfo.getTargetPathInWorkspace(project).getCanonicalFile()
                    targetLocations[targetPath].add(versionInfo)
                }

                if (module.versions.size() > 1) {
                    int noIncludesCount = 0
                    module.versions.any { String versionStr, UnpackModuleVersion versionInfo ->
                        !versionInfo.includeVersionNumberInPath
                    }
                    if (noIncludesCount > 0) {
                        project.logger.warn(
                            "Dependencies have been detected on different versions of the module '${module.name}'. " +
                            "To prevent different versions of this module being unpacked to the same location, " +
                            "the version number will be appended to the path as '${module.name}-<version>'. You can " +
                            "make this warning disappear by changing the locations to which these dependencies are " +
                            "being unpacked. For your information, here are the details of the affected dependencies:"
                        )
                        module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                            final String originalInfo = versionInfo.getIncludeInfo()
                            versionInfo.includeVersionNumberInPath = true
                            project.logger.warn(
                                "  ${module.group}:${module.name}:${versionStr} : "
                                + originalInfo + " -> " + versionInfo.getIncludeInfo()
                            )
                        }
                    }
                }
            }

            // Check if any target locations are used by more than one module/version.
            boolean foundTargetClash = targetLocations.inject(false) {
                boolean found, File target, Collection<UnpackModuleVersion> versions
             ->
                final boolean thisTargetHasClashes = versions.size() > 1
                if (thisTargetHasClashes) {
                    project.logger.error(
                        "In ${project}, location '${target}' is targeted by multiple dependencies/versions:"
                    )
                    versions.each { UnpackModuleVersion version ->
                        project.logger.error("    ${version.fullCoordinate} in configurations ${version.originalConfigurations}")
                        while (version.parent != null) {
                            version = version.parent
                            project.logger.error("        which is from ${version.fullCoordinate}")
                        }
                        project.logger.error("        which is from packed dependency ${version.packedDependency.name}")
                    }
                }
                return found || thisTargetHasClashes
            }
            if (foundTargetClash) {
                throw new RuntimeException("Multiple different dependencies/versions are targeting the same locations.")
            }

            unpackModules = new ArrayList(unpackModulesMap.values())
        }

        unpackModules
    }
}

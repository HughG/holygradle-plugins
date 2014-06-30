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
        Closure visitDependencyPredicate,
        Closure visitChildrenPredicate
    ) {
        // Note: This method used to have the predicates as optional arguments, where null meant "always true", but I
        // kept making mistakes with them, so clearly it was a bad idea.
        dependencies.each { resolvedDependency ->
            try {
                dependencyStack.push(resolvedDependency)
                println("tRD: ${dependencyStack.join(' <- ')}")

                if (visitDependencyPredicate(resolvedDependency)) {
                    dependencyAction(resolvedDependency)
                }

                if (visitChildrenPredicate(resolvedDependency)) {
                    traverseResolvedDependencies(
                        resolvedDependency.children,
                        dependencyStack,
                        dependencyAction,
                        visitDependencyPredicate,
                        visitChildrenPredicate
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
        Closure visitDependencyPredicate,
        Closure visitChildrenPredicate
    ) {
        // Note: This method used to have the predicates as optional arguments, where null meant "always true", but I
        // kept making mistakes with them, so clearly it was a bad idea.
        traverseResolvedDependencies(
            dependencies,
            new Stack<ResolvedDependency>(),
            dependencyAction,
            visitDependencyPredicate,
            visitChildrenPredicate
        )
    }

    private boolean isModuleInBuild(ModuleVersionIdentifier id) {
        return project.rootProject.allprojects.find {
            //project.logger.debug "$it <=> $id"
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
            },
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                // Visit this dependency only if we've haven't seen it already, and it's not a module we're building from
                // source (because we don't need an ivy file for that -- we have relative path info in the source dep).
                !result.containsKey(id) && !isModuleInBuild(id)
            },
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                // Visit this dependency;s children only if we've haven't seen it already.  We still want to search into
                // modules which are part of the source for this build, so we don't call isModuleInBuild.
                !result.containsKey(id)
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
    private static Map<ModuleVersionIdentifier, File> getResolvedIvyFiles(
        Set<ResolvedDependency> dependencies
    ) {
        Map<ModuleVersionIdentifier, File> ivyFiles = new HashMap<ModuleVersionIdentifier, File>()
        traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                final ModuleVersionIdentifier id = resolvedDependency.module.id
                final ResolvedArtifact art = resolvedDependency.moduleArtifacts.find { a -> a.name == "ivy" }
                final File file = art?.file
                ivyFiles[id] = file
            },
            { ResolvedDependency resolvedDependency ->
                // Skip this dependency if we've seen it already.
                !ivyFiles.containsKey(resolvedDependency.module.id)
            },
            { ResolvedDependency resolvedDependency ->
                // Skip this dependency's children if we've seen it already.
                !ivyFiles.containsKey(resolvedDependency.module.id)
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
        Map<ModuleVersionIdentifier, File> ivyFiles = ivyFileMaps[conf.name]
        if (ivyFiles == null) {
            Collection<Dependency> ivyDeps = getDependenciesForIvyFiles(
                conf.resolvedConfiguration.firstLevelModuleDependencies
            )
            //noinspection GroovyAssignabilityCheck
            Configuration ivyConf = project.configurations.detachedConfiguration(* ivyDeps)
            ivyFiles = getResolvedIvyFiles(ivyConf.resolvedConfiguration.firstLevelModuleDependencies)
            ivyFileMaps[conf.name] = ivyFiles
            project.logger.info("Resolved ivy files for ${conf}: ${ivyFiles.entrySet().join('\r\n')}")
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
        Configuration conf,
        Collection<PackedDependencyHandler> packedDependencies,
        Map<ModuleIdentifier, UnpackModule> unpackModules,
        Collection<ModuleVersionIdentifier> modulesWithoutIvyFiles,
        Set<ResolvedDependency> dependencies
    ) {
        project.logger.info("collectUnpackModules for ${conf}, starting with ${unpackModules.keySet().sort().join('\r\n')}")

        traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                // If we get here then we found an ivy file for the dependency, and we will just assume that we should
                // be unpacking it.  Ideally we should look for a custom XML tag that indicates that it was published
                // by the intrepid plugin. However, that would break compatibility with lots of artifacts that have
                // already been published.
                project.logger.info("collectUnpackModules: processing ${id}")

                // Find or create an UnpackModule instance.
                UnpackModule unpackModule = unpackModules[id.module]
                if (unpackModule == null) {
                    unpackModule = new UnpackModule(id.group, id.name)
                    unpackModules[id.module] = unpackModule
                    project.logger.info("collectUnpackModules: created module for ${id.module}")
                }

                // Find a parent UnpackModuleVersion instance i.e. one which has a dependency on
                // 'this' UnpackModuleVersion. There will only be a parent if this is a transitive
                // dependency.
                //
                // TODO: There could be more than one parent. Deal with it gracefully.  We might need to drop the "have
                // seen it before" filter from the visitDependencyPredicate.
                UnpackModuleVersion parentUnpackModuleVersion = null
                resolvedDependency.getParents().each { parentDependency ->
                    ModuleVersionIdentifier parentDependencyVersion = parentDependency.getModule().getId()
                    UnpackModule parentUnpackModule = unpackModules[parentDependencyVersion.module]
                    if (parentUnpackModule != null) {
                        parentUnpackModuleVersion = parentUnpackModule.getVersion(parentDependencyVersion)
                    }
                }

                // Find or create an UnpackModuleVersion instance.
                UnpackModuleVersion unpackModuleVersion
                if (unpackModule.versions.containsKey(id.version)) {
                    unpackModuleVersion = unpackModule.versions[id.version]
                } else {

                    // If this resolved dependency is a transitive dependency, "thisPackedDep"
                    // below will be null
                    PackedDependencyHandler thisPackedDep = packedDependencies.find {
                        (it.groupName == id.group) &&
                            (it.dependencyName == id.name) &&
                            (it.versionStr == id.version)
                    }

                    File ivyFile = getIvyFile(conf, resolvedDependency)
                    project.logger.info "collectUnpackModules/dA: Ivy file under ${conf} for ${id} is ${ivyFile}"
                    unpackModuleVersion = new UnpackModuleVersion(id, ivyFile, parentUnpackModuleVersion, thisPackedDep)
                    unpackModule.versions[id.version] = unpackModuleVersion
                    project.logger.info(
                        "collectUnpackModules: created version for ${id} " +
                        "(pUMV = ${parentUnpackModuleVersion}, tPD = ${thisPackedDep})"
                    )

                }

                project.logger.info("collectUnpackModules: adding ${id.module} conf ${conf.name} artifacts ${resolvedDependency.moduleArtifacts}")
                unpackModuleVersion.addArtifacts(resolvedDependency.getModuleArtifacts(), conf.name)
            },
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                // Visit this dependency only if: we've haven't seen it already for the configuration we're processing,
                // it's not a module we're building from source (because we don't want to include those as
                // UnpackModules), and we can find its ivy.xml file (because we need that for relativePath to other
                // modules).
                if (unpackModules[id.module]?.getVersion(id)?.configurations?.contains(conf)) {
                    project.logger.info("collectUnpackModules: Skipping ${id} because it has already been visited")
                    return false
                }

                if (isModuleInBuild(id)) {
                    project.logger.info("collectUnpackModules: Skipping ${id} because it is part of this build")
                    return false
                }

                // Is there an ivy file corresponding to this dependency?
                File ivyFile = getIvyFile(conf, resolvedDependency)
                if (ivyFile == null) {
                    modulesWithoutIvyFiles.add(id)
                    project.logger.error("collectUnpackModules: Failed to find location of ivy.xml file for ${id}")
                    // Continue anyway, so that other errors can be logged.
                    return false
                } else if (!ivyFile.exists()) {
                    modulesWithoutIvyFiles.add(id)
                    project.logger.error("collectUnpackModules: ivy.xml file for ${id} not found at ${ivyFile}")
                    // Continue anyway, so that other errors can be logged.
                    return false
                }

                project.logger.info "collectUnpackModules/vDP: Ivy file under ${conf} for ${id} is ${ivyFile}"
                return true
            },
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                // Visit this dependency's children only if: we've haven't seen it already for the configuration we're
                // processing, and we can find its ivy.xml file (because we need that for relativePath to other modules).
                return !(unpackModules[id.module]?.getVersion(id)?.configurations?.contains(conf)) &&
                    getIvyFile(conf, resolvedDependency)?.exists()
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
            project.logger.info("getAllUnpackModules for ${project}")

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
            Map<File, Collection<String>> targetLocations = [:].withDefault { new ArrayList<String>() }
            unpackModulesMap.values().each { UnpackModule module ->
                module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                    File targetPath = versionInfo.getTargetPathInWorkspace(project).getCanonicalFile()
                    targetLocations[targetPath].add(versionInfo.getFullCoordinate())
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
            targetLocations.each { File target, Collection<String> coordinates ->
                if (coordinates.size() > 1) {
                    throw new RuntimeException(
                        "Multiple different modules/versions are targetting the same location. " +
                            "'${target}' is being targetted by: ${coordinates}. That's not going to work."
                    )
                }
            }


            unpackModules = new ArrayList(unpackModulesMap.values())
        }

        unpackModules
    }
}

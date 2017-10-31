package holygradle.unpacking

import holygradle.dependencies.DependenciesStateHandler
import holygradle.dependencies.PackedDependencyHandler
import holygradle.dependencies.ResolvedDependenciesVisitor
import org.gradle.api.Project
import org.gradle.api.artifacts.*

/**
 * This project extension provides information about the state of packed dependencies when they are resolved.
 *
 * Core Gradle holds information about the dependencies of a project in a structure like this:
 *
 *   project 1-->* configuration *-->* module_with_version *-->* module_with_version
 *
 * Each module_with_version may have dependencies on specific other module versions.  There are parallel structures for
 * the requested and resolved sets of module.  The requested structure may contain multiple versions of the same module,
 * for any given configuration; but the resolved structure will have only one version of each module, per configuration.
 * If a project needs to use multiple resolved versions of a module, it must reference them from different
 * configurations.
 *
 * The Holy Gradle's intrepid plugin, Perhaps through historical lack of understanding of the design of Gradle, tries to
 * "put" all resolved module versions into one structure per project, not divided by configuration.  In saying "put", I
 * mean specifically that it does this in order to create, in the project's folder hierarchy, links to the unzipped
 * versions of a module's artifacts, in the "unpackCache" folder in the Gradle home dir.  The structure of this
 * information is
 *
 *   project 1-->* UnpackModule 1-->* UnpackModuleVersion 1-->1+ configuration_name
 *
 * Each UnpackModuleVersion also holds
 *   - a mapping from resolved artifacts to the names of the project configurations which caused them to be included;
 *   - a mapping from the IDs of its dependencies to the relative paths at which each should be linked
 *
 * (The implementation around this arrangement has a couple of problems: it is very awkward to use multiple versions of
 * the same module; and it is not possible to link the same module version in at multiple locations -- although the
 * latter is intended to be supported, I think.)
 *
 * The purpose of this class is to build the latter structure from the former.
 */
class PackedDependenciesStateHandler implements PackedDependenciesStateSource {
    private final Project project
    private final DependenciesStateHandler dependenciesStateHandler
    private Map<Configuration, Collection<UnpackModule>> unpackModules = null

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
        this.dependenciesStateHandler = project.extensions.dependenciesState
    }

    /**
     * This method visits the resolved {@code dependencies} of the {@code originalConf} (a configuration of a project in the build),
     * both direct and transitive, and fills in the {@code unpackModules} map for them.  It will create a new
     * UnpackModule for a dependency if it doesn't yet exist, and add a new UnpackModuleVersion to it if it doesm for
     * each dependency it visits.  It will also populate the {@code modulesWithoutIvyFiles} collection with any modules
     * for which an Ivy XML file could not be found.
     *
     * @param originalConf A configuration of the project to which this handler belongs, in the context of which
     * dependencies are to be resolved.
     * @param packedDependencies The {@link PackedDependencyHandler} instances for this handler's project.
     * @param unpackModules A map of {@link UnpackModule} instances, to be populated corresponding to the transitive
     * closure of dependencies.
     * @param modulesWithoutIvyFiles A collection of module version IDs, to be populated with IDs of any modules for
     * which no Ivy XML file could be found.
     * @param dependencies The top-level set of resolved dependencies
     */
    private void collectUnpackModules(
        Configuration originalConf,
        Set<ResolvedDependency> dependencies,
        Collection<PackedDependencyHandler> packedDependencies,
        Map<ModuleIdentifier, UnpackModule> unpackModules,
        Collection<ModuleVersionIdentifier> modulesWithoutIvyFiles
    ) {
        project.logger.debug("collectUnpackModules for ${originalConf}")
        project.logger.debug("    starting with unpackModules ${unpackModules.keySet().sort().join('\r\n')}")
        project.logger.debug("    and packedDependencies ${packedDependencies*.name.sort().join('\r\n')}")

        ResolvedDependenciesVisitor.traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                // Visit this dependency only if: it's not a module we're building from source (because we don't want to
                // include those as UnpackModules), and we can find its ivy.xml file (because we need that for
                // relativePath to other modules).
                //
                // Visit this dependency's children only if: we can find its ivy.xml file (because we need that for
                // relativePath to other modules).
                //
                // (Previously we wouldn't re-visit the module or its children if we'd already seen this
                // resolvedDependency.configuration of this module.  However, this check was removed in 45f38934dea9
                // for ticket GR #6279 (mis-labelled in the commit) because ...TODO )

                ModuleVersionIdentifier id = resolvedDependency.module.id
                final boolean isNonBuildModule = !dependenciesStateHandler.isModuleInBuild(id)
                if (!isNonBuildModule) {
                    project.logger.debug("collectUnpackModules: Skipping ${id} because it is part of this build")
                }

                // Is there an ivy file corresponding to this dependency?
                File ivyFile = dependenciesStateHandler.getIvyFile(originalConf, resolvedDependency)
                final boolean ivyFileExists = ivyFile?.exists()
                // If this is a new module, not part of the build, but we can't find its ivy file, then track it so
                // we can throw an exception later (after we've checked all dependencies).
                if (isNonBuildModule && !ivyFileExists) {
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
                return new ResolvedDependenciesVisitor.VisitChoice(
                    isNonBuildModule && ivyFileExists,
                    ivyFileExists
                )
            },
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

                // Find all parent UnpackModuleVersion instances, i.e., ones which have a dependency on 'this'
                // UnpackModuleVersion. There will only be parents if this is a transitive dependency, and not if it
                // is only a direct dependency of the project.
                Set<UnpackModuleVersion> parents = new HashSet<>()
                for (parentDependency in resolvedDependency.parents) {
                    ModuleVersionIdentifier parentDependencyVersion = parentDependency.module.id
                    UnpackModule parentUnpackModule = unpackModules[parentDependencyVersion.module]
                    UnpackModuleVersion version = parentUnpackModule?.getVersion(parentDependencyVersion)
                    if (version != null) {
                        parents.add(version)
                    }
                }

                // We only have PackedDependencyHandlers for direct dependencies of a project.  If this resolved
                // dependency is a transitive dependency, "thisPackedDep" will be null.
                PackedDependencyHandler thisPackedDep = packedDependencies.find {
                    // Note that we don't compare the version, because we might have resolved to another version if
                    // multiple versions were requested in the dependency graph.  In that case, we still want to
                    // regard this packed dependency as mapping to the given UnpackModuleVersion, i.e., to the
                    // resolved dependency version. We know that there can only be one resolved version per
                    // configuration so by checking the configuration matches we can be sure this is it.
                    return (it.groupName == id.group) &&
                            (it.dependencyName == id.name) &&
                            (it.configurationMappings.any { entry ->
                                originalConf.hierarchy.any { it.name == entry.key }
                            })
                }

                // Find or create an UnpackModuleVersion instance.
                UnpackModuleVersion unpackModuleVersion
                if (unpackModule.versions.containsKey(id.version)) {
                    unpackModuleVersion = unpackModule.versions[id.version]
                    unpackModuleVersion.addParents(parents)
                    // If the same module appears as both a direct packed dependency (for which the path is explicitly
                    // specified by the user) and a transitive packed dependency (for which the path is inferred from
                    // its ancestors), we want to use the explicitly specified path.  Because UnpackModuleVersion
                    // prioritises the properties of a packed dependency if one is set we can achieve this by setting
                    // the packed dependency property here even if a transitive dependency has created the module first.
                    if (thisPackedDep != null) {
                        // It's possible for someone to specify the same version of the same module at two different
                        // paths, using two different packed dependencies.  However, we regard that as too complicated
                        // and confusing, and don't allow it.  If they really need it to appear to be in two places they
                        // can create explicit links.
                        final PackedDependencyHandler existingPackedDep = unpackModuleVersion.packedDependency
                        if (existingPackedDep != null) {
                            // If the existing packed dep is the same as the one we found, we don't need to change it.
                            // If it's different, fail.
                            if (existingPackedDep != thisPackedDep) {
                                throw new RuntimeException(
                                    "Module version ${id} is specified by packed dependencies at both path " +
                                        "'${existingPackedDep.name}' and '${thisPackedDep.name}'.  " +
                                        "A single version can only be specified at one path.  If you need it to " +
                                        "appear at " +
                                        "more than one location you can explicitly create links."
                                )
                            }
                        } else {
                            // There was no existing packed dep, so we fill it in.
                            unpackModuleVersion.packedDependency = thisPackedDep
                        }
                    }
                } else {
                    unpackModuleVersion = new UnpackModuleVersion(id, parents, thisPackedDep)
                    unpackModule.versions[id.version] = unpackModuleVersion
                    project.logger.debug(
                            "collectUnpackModules: created version for ${id} " +
                                    "(parents = ${parents}, tPD = ${thisPackedDep?.name})"
                    )
                }

                project.logger.debug("collectUnpackModules: adding ${id.module} originalConf ${originalConf.name} artifacts ${resolvedDependency.moduleArtifacts}")
                unpackModuleVersion.addArtifacts(resolvedDependency.getModuleArtifacts(), originalConf.name)
            }
        )
    }

    /**
     * Returns a collection of objects representing the transitive set of unpacked modules used by the project.
     *
     * @return The transitive set of unpacked modules used by the project.
     */
    public Map<Configuration, Collection<UnpackModule>> getAllUnpackModules() {
        if (unpackModules == null) {
            unpackModules = initializeAllUnpackModules()
        }
        unpackModules
    }

    private Map<Configuration, Collection<UnpackModule>> initializeAllUnpackModules() {
        project.logger.debug("getAllUnpackModules for ${project}")

        final packedDependencies = project.packedDependencies as Collection<PackedDependencyHandler>
        // Build a map of all unpack modules per configuration.  This allows us to force an unpack module to have only
        // one location per configuration (by setting its packedDependency or its parents) but allow different locations
        // for different configurations.  (A different configuration may have some, but not all, module versions
        // different.  If the same version of a transitive dependency appears in more that one configuration, we want to
        // link it next to its originating packed dependency/-ies in *all* configurations, not only in the first one we
        // happen to process.)
        Map<Configuration, Map<ModuleIdentifier, UnpackModule>> configurationUnpackModulesMap =
            [:].withDefault { new HashMap<ModuleIdentifier, UnpackModule>() }

        // Build a list (without duplicates) of all artifacts the project depends on.
        Collection<ModuleVersionIdentifier> modulesWithoutIvyFiles = new HashSet<ModuleVersionIdentifier>()
        project.configurations.each((Closure){ Configuration conf ->
            ResolvedConfiguration resConf = conf.resolvedConfiguration
            collectUnpackModules(
                conf,
                resConf.getFirstLevelModuleDependencies(),
                packedDependencies,
                configurationUnpackModulesMap[conf],
                modulesWithoutIvyFiles
            )
        })

        if (!modulesWithoutIvyFiles.isEmpty()) {
            throw new RuntimeException("Some dependencies had no ivy.xml file")
        }

        // Build a map of target locations to module versions
        List<UnpackModule> flattenedUnpackModules = configurationUnpackModulesMap.values().collect { it.values() }.flatten()
        Map<File, Collection<UnpackModuleVersion>> targetLocations =
            [:].withDefault { new ArrayList<UnpackModuleVersion>() }
        flattenedUnpackModules.each { UnpackModule module ->
            module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                File targetPath = versionInfo.getTargetPathInWorkspace(project).getCanonicalFile()
                targetLocations[targetPath].add(versionInfo)
            }
        }

        // Check if any target locations are used by more than one module/version with different unpack entries.  It's
        // okay if there are multiple but they all unpack to the same thing/place.  (We use "inject", instead of "any",
        // because we want to keep checking even after we find a problem, so we can log them all.)
        boolean foundTargetClash = targetLocations.inject(false) {
            boolean found, File target, Collection<UnpackModuleVersion> versions
             ->
            final Set<UnpackDirEntry> uniqueUnpackDirEntries = versions.collect { it.getUnpackDirEntry(project) }.toSet()
            final boolean thisTargetHasClashes = uniqueUnpackDirEntries.size() > 1
            if (thisTargetHasClashes) {
                project.logger.error(
                    "In ${project}, location '${target}' is targeted by multiple dependencies/versions:"
                )
                versions.each {
                    logUnpackModuleVersionAncestry(it, 1)
                    project.logger.debug("    "+ it.getUnpackEntry(project).toString())
                }
                project.logger.debug("  Unique unpack dir entries: " + uniqueUnpackDirEntries)
            }
            return found || thisTargetHasClashes
        }
        if (foundTargetClash) {
            throw new RuntimeException("Multiple different dependencies/versions are targeting the same locations.")
        }

        def map = new HashMap<Configuration, Collection<UnpackModule>>()
        unpackModules = configurationUnpackModulesMap.collectEntries(map) { [it.key, it.value.values()] }
        unpackModules
    }

    private void logUnpackModuleVersionAncestry(UnpackModuleVersion version, int indent) {
        String prefix = "    " * indent
        project.logger.error("${prefix}${version.fullCoordinate} in configurations ${version.originalConfigurations}")
        PackedDependencyHandler packedDependency = version.packedDependency
        if (packedDependency != null) {
            project.logger.error("${prefix}  directly from packed dependency ${packedDependency.name}")
        }
        version.parents.each { parent ->
            project.logger.error("${prefix}  indirectly from ${version.fullCoordinate}")
            logUnpackModuleVersionAncestry(parent, indent + 1)
        }

    }
}

package holygradle.unpacking

import holygradle.artifacts.ConfigurationHelper
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
        Map<ModuleIdentifier, UnpackModule> unpackModules
    ) {
        project.logger.debug("collectUnpackModules for ${originalConf}")
        project.logger.debug("    starting with unpackModules ${unpackModules.keySet().sort().join('\r\n')}")
        project.logger.debug("    and packedDependencies ${packedDependencies*.name.sort().join('\r\n')}")
        project.logger.info("collectUnpackModules:   origConf hierarchy names: ${originalConf.hierarchy*.name}")

        Set<ResolvedDependenciesVisitor.ResolvedDependencyId> dependencyConfigurationsAlreadySeen =
            new HashSet<ResolvedDependenciesVisitor.ResolvedDependencyId>()

        ResolvedDependenciesVisitor.traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                // Visit this dependency only if: we've haven't seen it already for the configuration we're
                // processing, it's not a module we're building from source (because we don't want to include those
                // as UnpackModules).
                //
                // Visit this dependency's children only if: we've haven't seen it already for the configuration
                // we're processing.

                ModuleVersionIdentifier id = resolvedDependency.module.id

                final ResolvedDependenciesVisitor.ResolvedDependencyId newId =
                    new ResolvedDependenciesVisitor.ResolvedDependencyId(id, resolvedDependency.configuration)
                project.logger.debug("Adding ${newId} to ${dependencyConfigurationsAlreadySeen}")
                final boolean isNewModuleConfiguration = dependencyConfigurationsAlreadySeen.add(newId)
                if (!isNewModuleConfiguration) {
                    project.logger.debug(
                        "collectUnpackModules: Skipping ${id}, conf ${resolvedDependency.configuration}, " +
                        "because it has already been visited"
                    )
                }

                final boolean moduleIsInBuild = dependenciesStateHandler.isModuleInBuild(id)
                if (moduleIsInBuild) {
                    project.logger.debug("collectUnpackModules: Skipping ${id} because it is part of this build")
                }

                final boolean isNewNonBuildModuleConfiguration = isNewModuleConfiguration && !moduleIsInBuild

                return new ResolvedDependenciesVisitor.VisitChoice(
                    isNewNonBuildModuleConfiguration,
                    isNewModuleConfiguration
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

                // Find a parent UnpackModuleVersion instance i.e. one which has a dependency on 'this'
                // UnpackModuleVersion. There will only be a parent if this is a transitive dependency, and not if it
                // is a direct dependency of the project.
                UnpackModuleVersion parentUnpackModuleVersion =
                    resolvedDependency.getParents().findResult { parentDependency ->
                        ModuleVersionIdentifier parentDependencyVersion = parentDependency.module.id
                        UnpackModule parentUnpackModule = unpackModules[parentDependencyVersion.module]
                        parentUnpackModule?.getVersion(parentDependencyVersion)
                    }

                // We only have PackedDependencyHandlers for direct dependencies of a project.  If this resolved
                // dependency is a transitive dependency, "thisPackedDep" will be null.
                PackedDependencyHandler thisPackedDep = packedDependencies.find {
                    // Note that we don't compare the version, because we might have resolved to another version if
                    // multiple versions were requested in the dependency graph.  In that case, we still want to
                    // regard this packed dependency as mapping to the given UnpackModuleVersion, i.e., to the
                    // resolved dependency version. We know that there can only be one resolved version per
                    // configuration so by checking the configuration matches we can be sure this is it.
                    project.logger.info("collectUnpackModules: comparing PD ${it.dependencyId} to ${id}")
                    project.logger.info("collectUnpackModules:   PD conf map keys: ${it.configurationMappings*.key}")
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
                    // If the same module appears as both a direct packed dependency (for which the path is explicitly
                    // specified by the user) and a transitive packed dependency (for which the path is inferred from
                    // its ancestors), we want to use the explicitly specified path.  Because UnpackModuleVersion
                    // prioritises the properties of a packed dependency if one is set we can achieve this by setting
                    // the packed dependency property here even if a transitive dependency has created the module first.
                    if (thisPackedDep != null) {
                        // It's possible for someone to specify the same version of the same module at two different
                        // paths, using two different packed dependencies.  However, we regard that as too complicated
                        // and confusing, and don't allow it.  If they really need it to appear to be in two places they
                        // can create explicit symlinks.
                        final PackedDependencyHandler existingPackedDep = unpackModuleVersion.packedDependency
                        if (existingPackedDep != null && existingPackedDep != thisPackedDep) {
                            throw new RuntimeException(
                                "Module version ${id} is specified by packed dependencies at both path " +
                                "'${existingPackedDep.name}' and '${thisPackedDep.name}'.  " +
                                "A single version can only be specified at one path.  If you need it to appear at " +
                                "more than one location you can explicitly create links."
                            )
                        }
                        unpackModuleVersion.packedDependency = thisPackedDep
                    }
                } else {
                    unpackModuleVersion = new UnpackModuleVersion(id, parentUnpackModuleVersion, thisPackedDep)
                    unpackModule.versions[id.version] = unpackModuleVersion
                    project.logger.debug(
                        "collectUnpackModules: created version for ${id} " +
                        "(pUMV = ${parentUnpackModuleVersion}, tPD = ${thisPackedDep?.name})"
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
    public Collection<UnpackModule> getAllUnpackModules() {
        if (unpackModulesMap == null) {
            unpackModules = initializeAllUnpackModules()
        }
        unpackModules
    }

    private ArrayList initializeAllUnpackModules() {
        project.logger.debug("getAllUnpackModules for ${project}")

        final packedDependencies = project.packedDependencies as Collection<PackedDependencyHandler>
        // Build a list (without duplicates) of all artifacts the project depends on.
        unpackModulesMap = [:]
        project.configurations.each((Closure){ Configuration conf ->
            Set<ResolvedDependency> firstLevelDeps =
                ConfigurationHelper.getFirstLevelModuleDependenciesForMaybeOptionalConfiguration(conf)
            collectUnpackModules(
                conf,
                firstLevelDeps,
                packedDependencies,
                unpackModulesMap
            )
        })

        // Build a map of target locations to module versions
        Map<File, Collection<UnpackModuleVersion>> targetLocations =
            [:].withDefault { new ArrayList<UnpackModuleVersion>() }
        unpackModulesMap.values().each { UnpackModule module ->
            module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                File targetPath = versionInfo.getTargetPathInWorkspace(project).getCanonicalFile()
                targetLocations[targetPath].add(versionInfo)
            }
        }

        // Check if any target locations are used by more than one module/version.  (We use "inject", instead of "any",
        // because we want to keep checking even after we find a problem, so we can log them all.)
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
        unpackModules
    }
}

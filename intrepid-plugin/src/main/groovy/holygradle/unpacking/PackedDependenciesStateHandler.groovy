package holygradle.unpacking

import holygradle.dependencies.DependenciesStateHandler
import holygradle.dependencies.PackedDependencyHandler
import holygradle.dependencies.ResolvedDependenciesVisitor
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier

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
 * mean specifically that it does this in order to create, in the project's folder hierarchy, symlinks to the unzipped
 * versions of a module's artifacts, in the "unpackCache" folder in the Gradle home dir.  The structure of this
 * information is
 *
 *   project 1-->* UnpackModule 1-->* UnpackModuleVersion 1-->1+ configuration_name
 *
 * Each UnpackModuleVersion also holds
 *   - a mapping from resolved artifacts to the names of the project configurations which caused them to be included;
 *   - a mapping from the IDs of its dependencies to the relative paths at which each should be symlinked
 *
 * (The implementation around this arrangement has a couple of problems: it is very awkward to use multiple versions of
 * the same module; and it is not possible to symlink the same module version in at multiple locations -- although the
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
        Map<ModuleIdentifier, UnpackModule> unpackModules,
        Collection<ModuleVersionIdentifier> modulesWithoutIvyFiles
    ) {
        project.logger.debug("collectUnpackModules for ${originalConf}")
        project.logger.debug("    starting with unpackModules ${unpackModules.keySet().sort().join('\r\n')}")
        project.logger.debug("    and packedDependencies ${packedDependencies*.name.sort().join('\r\n')}")

        Set<ResolvedDependenciesVisitor.ResolvedDependencyId> dependencyConfigurationsAlreadySeen =
            new HashSet<ResolvedDependenciesVisitor.ResolvedDependencyId>()

        ResolvedDependenciesVisitor.traverseResolvedDependencies(
            dependencies,
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

                // Is there an ivy file corresponding to this dependency?
                File ivyFile = dependenciesStateHandler.getIvyFile(originalConf, resolvedDependency)
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
                return new ResolvedDependenciesVisitor.VisitChoice(
                    isNewNonBuildModuleConfiguration && ivyFileExists,
                    isNewModuleConfiguration && ivyFileExists
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

                    // We only have PackedDependencyHandlers for direct dependencies of a project.  If this resolved
                    // dependency is a transitive dependency, "thisPackedDep" will be null.
                    PackedDependencyHandler thisPackedDep = packedDependencies.find {
                        // Note that we don't compare the version, because we might have resolved to another version if
                        // multiple versions were requested in the dependency graph.  In that case, we still want to
                        // regard this packed dependency as mapping to the given UnpackModuleVersion, i.e., to the
                        // resolved dependency version.
                        return (it.groupName == id.group) &&
                            (it.dependencyName == id.name)
                    }

                    File ivyFile = dependenciesStateHandler.getIvyFile(originalConf, resolvedDependency)
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
        Collection<ModuleVersionIdentifier> modulesWithoutIvyFiles = new HashSet<ModuleVersionIdentifier>()
        project.configurations.each { conf ->
            ResolvedConfiguration resConf = conf.resolvedConfiguration
            collectUnpackModules(
                conf,
                resConf.getFirstLevelModuleDependencies(),
                packedDependencies,
                unpackModulesMap,
                modulesWithoutIvyFiles
            )
        }

        if (!modulesWithoutIvyFiles.isEmpty()) {
            throw new RuntimeException("Some dependencies had no ivy.xml file")
        }

        // Check if we need to force the version number to be included in the path in order to prevent
        // two different versions of a module to be unpacked to the same location.
        Map<File, Collection<UnpackModuleVersion>> targetLocations =
            [:].withDefault { new ArrayList<UnpackModuleVersion>() }
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

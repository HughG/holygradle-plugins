package holygradle.unpacking

import holygradle.artifacts.ConfigurationHelper
import holygradle.dependencies.DependenciesStateHandler
import holygradle.dependencies.PackedDependencyHandler
import holygradle.dependencies.ResolvedDependenciesVisitor
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import holygradle.kotlin.dsl.get
import holygradle.util.addingDefault
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import java.io.File

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
open class PackedDependenciesStateHandler(
        private val project: Project
) : PackedDependenciesStateSource {
    companion object {
        @JvmStatic
        fun createExtension(project: Project): PackedDependenciesStateHandler {
            project.extensions.add("packedDependenciesState", PackedDependenciesStateHandler(project))
            return project.extensions["packedDependenciesState"] as PackedDependenciesStateHandler
        }
    }

    private val dependenciesStateHandler: DependenciesStateHandler =
            project.extensions.getByName("dependenciesState") as DependenciesStateHandler
    private var initializedUnpackModules = false
    private lateinit var unpackModulesMap: Map<ModuleIdentifier, UnpackModule>
    private lateinit var unpackModules: Collection<UnpackModule>

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
    private fun collectUnpackModules(
        originalConf: Configuration,
        dependencies: Set<ResolvedDependency>,
        packedDependencies: Collection<PackedDependencyHandler>,
        unpackModules: MutableMap<ModuleIdentifier, UnpackModule>
    ) {
        project.logger.debug("collectUnpackModules for ${originalConf}")
        project.logger.debug("    starting with unpackModules ${unpackModules.keys.sortedBy { it.toString() }.joinToString("\r\n")}")
        project.logger.debug("    and packedDependencies ${packedDependencies.map { it.name }.sortedBy { it }.joinToString("\r\n")}")

        val dependencyConfigurationsAlreadySeen = mutableSetOf<ResolvedDependenciesVisitor.ResolvedDependencyId>()

        ResolvedDependenciesVisitor.traverseResolvedDependencies(
            dependencies,
            { resolvedDependency ->
                // Visit this dependency only if: we've haven't seen it already for the configuration we're
                // processing, it's not a module we're building from source (because we don't want to include those
                // as UnpackModules).
                //
                // Visit this dependency's children only if: we've haven't seen it already for the configuration
                // we're processing.

                val id = resolvedDependency.module.id

                val newId = ResolvedDependenciesVisitor.ResolvedDependencyId(id, resolvedDependency.configuration)
                project.logger.debug("Adding ${newId} to ${dependencyConfigurationsAlreadySeen}")
                val isNewModuleConfiguration = dependencyConfigurationsAlreadySeen.add(newId)
                if (!isNewModuleConfiguration) {
                    project.logger.debug(
                        "collectUnpackModules: Skipping ${id}, conf ${resolvedDependency.configuration}, " +
                        "because it has already been visited"
                    )
                }

                val moduleIsInBuild = dependenciesStateHandler.isModuleInBuild(id)
                if (moduleIsInBuild) {
                    project.logger.debug("collectUnpackModules: Skipping ${id} because it is part of this build")
                }

                val isNewNonBuildModuleConfiguration = isNewModuleConfiguration && !moduleIsInBuild

                ResolvedDependenciesVisitor.VisitChoice(
                    isNewNonBuildModuleConfiguration,
                    isNewNonBuildModuleConfiguration
                )
            },
            { resolvedDependency ->
                val id = resolvedDependency.module.id
                // If we get here then we found an ivy file for the dependency, and we will just assume that we should
                // be unpacking it.  Ideally we should look for a custom XML tag that indicates that it was published
                // by the intrepid plugin. However, that would break compatibility with lots of artifacts that have
                // already been published.
                project.logger.debug("collectUnpackModules: processing ${id}")

                // Find or create an UnpackModule instance.
                var unpackModule = unpackModules[id.module]
                if (unpackModule == null) {
                    unpackModule = UnpackModule(id.group, id.name)
                    unpackModules[id.module] = unpackModule
                    project.logger.debug("collectUnpackModules: created module for ${id.module}")
                }

                // Find a parent UnpackModuleVersion instance i.e. one which has a dependency on 'this'
                // UnpackModuleVersion. There will only be a parent if this is a transitive dependency, and not if it
                // is a direct dependency of the project.
                val parentUnpackModuleVersion: UnpackModuleVersion? =
                    resolvedDependency.parents.firstNotNullResult { parentDependency ->
                        val parentDependencyVersion = parentDependency.module.id
                        val parentUnpackModule = unpackModules[parentDependencyVersion.module]
                        parentUnpackModule?.getVersion(parentDependencyVersion)
                    }

                // We only have PackedDependencyHandlers for direct dependencies of a project.  If this resolved
                // dependency is a transitive dependency, "thisPackedDep" will be null.
                val thisPackedDep = packedDependencies.find {
                    // Note that we don't compare the version, because we might have resolved to another version if
                    // multiple versions were requested in the dependency graph.  In that case, we still want to
                    // regard this packed dependency as mapping to the given UnpackModuleVersion, i.e., to the
                    // resolved dependency version. We know that there can only be one resolved version per
                    // configuration so by checking the configuration matches we can be sure this is it.
                    (it.groupName == id.group) &&
                        (it.dependencyName == id.name) &&
                        (it.configurationMappings.any { entry ->
                            originalConf.hierarchy.any { it.name == entry.key }
                        })
                }

                // Find or create an UnpackModuleVersion instance.
                val unpackModuleVersion: UnpackModuleVersion
                val moduleVersion = unpackModule.versions[id.version]
                if (moduleVersion != null) {
                    unpackModuleVersion = moduleVersion
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
                        val existingPackedDep = unpackModuleVersion.packedDependency
                        if (existingPackedDep != null && existingPackedDep != thisPackedDep) {
                            throw RuntimeException(
                                "Module version ${id} is specified by packed dependencies at both path " +
                                "'${existingPackedDep.name}' and '${thisPackedDep.name}'.  " +
                                "A single version can only be specified at one path.  If you need it to appear at " +
                                "more than one location you can explicitly create links."
                            )
                        }
                        unpackModuleVersion.packedDependency = thisPackedDep
                    }
                } else {
                    unpackModuleVersion = UnpackModuleVersion(project, id, parentUnpackModuleVersion, thisPackedDep)
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
    override val allUnpackModules: Collection<UnpackModule>
        get() {
            if (!initializedUnpackModules) {
                initializeAllUnpackModules()
            }
            return unpackModules
        }

    private fun initializeAllUnpackModules() {
        project.logger.debug("getAllUnpackModules for ${project}")

        val deps = project.extensions.findByName("packedDependencies") as Collection<*>
        val packedDependencies = deps.filterIsInstance<PackedDependencyHandler>()
        // Build a list (without duplicates) of all artifacts the project depends on.
        val modulesMap = mutableMapOf<ModuleIdentifier, UnpackModule>()
        project.configurations.forEach{ conf ->
            val firstLevelDeps =
                ConfigurationHelper.getFirstLevelModuleDependenciesForMaybeOptionalConfiguration(conf)
            collectUnpackModules(
                conf,
                firstLevelDeps,
                packedDependencies,
                modulesMap
            )
        }

        // Build a map of target locations to module versions
        val targetLocations =
                mutableMapOf<File, MutableCollection<UnpackModuleVersion>>().addingDefault { mutableListOf<UnpackModuleVersion>() }
        for (module in modulesMap.values) {
            for ((_, versionInfo) in module.versions) {
                val targetPath = versionInfo.targetPathInWorkspace.canonicalFile
                targetLocations[targetPath]!!.add(versionInfo)
            }
        }

        // Check if any target locations are used by more than one module/version.  (We use "inject", instead of "any",
        // because we want to keep checking even after we find a problem, so we can log them all.)
        val foundTargetClash = targetLocations.entries.fold(false) { found, (target, versions) ->
            val thisTargetHasClashes = versions.size > 1
            if (thisTargetHasClashes) {
                project.logger.error(
                    "In ${project}, location '${target}' is targeted by multiple dependencies/versions:"
                )
                for (version in versions) {
                    project.logger.error("    ${version.fullCoordinate} in configurations ${version.originalConfigurations}")
                    var ver = version
                    var parent = ver.parent
                    while (parent != null) {
                        ver = parent
                        parent = ver.parent
                        project.logger.error("        which is from ${version.fullCoordinate}")
                    }
                    project.logger.error("        which is from packed dependency ${version.packedDependency!!.name}")
                }
            }
            found || thisTargetHasClashes
        }
        if (foundTargetClash) {
            throw RuntimeException("Multiple different dependencies/versions are targeting the same locations.")
        }

        unpackModules = modulesMap.values.toList()
        unpackModulesMap = modulesMap
    }
}

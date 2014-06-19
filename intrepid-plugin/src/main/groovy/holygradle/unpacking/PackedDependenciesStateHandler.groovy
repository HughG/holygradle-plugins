package holygradle.unpacking

import holygradle.dependencies.PackedDependencyHandler
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

class PackedDependenciesStateHandler {
    // TODO 2016-09-10 HughG: Use lenient?

    private static class IvyState {
        public Configuration ivyConf
        public Map<ModuleVersionIdentifier, File> ivyFiles = [:]

        public IvyState(Configuration ivyConf) {
            this.ivyConf = ivyConf
        }
    }
    // Map of "original configuration name" -> ivy state
    private Map<String,IvyState> ivyStates = new HashMap()

    private final Project project
    private Collection<UnpackModule> unpackModules = null

    public static PackedDependenciesStateHandler createExtension(Project project) {
        project.extensions.packedDependenciesState = new PackedDependenciesStateHandler(project)
        project.extensions.packedDependenciesState
    }

    public PackedDependenciesStateHandler(Project project) {
        this.project = project
    }

    /**
     * Calls a closure for all {@link org.gradle.api.artifacts.ResolvedDependency} instances in the transitive graph, selecting whether or not
     * to visit the children of eah using a predicate.  The same dependency may be visited more than once, if it appears
     * in the graph more than once.
     * @param dependencies The initial set of dependencies.
     * @param dependencyAction The closure to call for each {@link org.gradle.api.artifacts.ResolvedDependency}.
     * @param visitChildrenPredicate A predicate closure to call for {@link org.gradle.api.artifacts.ResolvedDependency} to decide whether to
     * visit its children.
     */
    private static void traverseResolvedDependencies(
        Set<ResolvedDependency> dependencies,
        Closure dependencyAction,
        Closure visitChildrenPredicate
    ) {
        println "Traversing ${dependencies.collect({ "${it.name} under ${it.configuration}" })}"
        dependencies.each { resolvedDependency ->
            dependencyAction(resolvedDependency)

            if (visitChildrenPredicate(resolvedDependency)) {
                traverseResolvedDependencies(resolvedDependency.children, dependencyAction, visitChildrenPredicate)
            }
        }
    }

    /**
     * Calls a closure for all {@link ResolvedDependency} instances in the transitive graph.  The same dependency may be
     * visited more than once, if it appears in the graph more than once.
     * @param dependencies The initial set of dependencies.
     * @param dependencyAction The closure to call for each {@link ResolvedDependency}.
     */
    private static void traverseResolvedDependencies(
        Set<ResolvedDependency> dependencies,
        Closure dependencyAction
    ) {
        traverseResolvedDependencies(dependencies, dependencyAction, { true })
    }

    private Collection<Dependency> getDependenciesForIvyFiles(
        Set<ResolvedDependency> dependencies
    ) {
        project.logger.debug "getDependenciesForIvyFiles(${project}, ...)"
        List<Dependency> result = []

        traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id

                // First, check whether this matches any project in our build.  If so, there will be no ivy file we
                // could resolve, so just ignore it.
                //
                // TODO 2013-06-09 HughG: Do we need to filter out any other kinds of dependencies?
                if (project.rootProject.allprojects.find {
                    //project.logger.debug "$it <=> $id"
                    it.group == id.group &&
                        it.name == id.name &&
                        it.version == id.version
                }
                ) {
                    return
                }

                ExternalModuleDependency dep = new DefaultExternalModuleDependency(
                    id.group, id.name, id.version, resolvedDependency.configuration
                )
                dep.artifact { art ->
                    art.name = "ivy"
                    art.type = "ivy"
                    art.extension = "xml"
                }
                result.add(dep)
                project.logger.debug "Added $dep"
            }
        )

        return result
    }

    private void collectResolvedIvyFiles(
        IvyState ivyState, Set<ResolvedDependency> dependencies
    ) {
        project.logger.debug "collectResolvedIvyFiles(${ivyState.ivyConf}, ...)"
        traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                final ModuleVersionIdentifier id = resolvedDependency.module.id
                project.logger.debug "${id} artifacts = ${resolvedDependency.moduleArtifacts}"
                final ResolvedArtifact art = resolvedDependency.moduleArtifacts.find { a -> a.name == "ivy" }
                project.logger.debug "${id} ivy artifact = ${art}, file = ${art?.file}"
                final File file = art?.file
                project.logger.debug "${id} ivy file = ${file}"
                ivyState.ivyFiles[id] = file
            }
        )
    }

    // Get the ivy file for the resolved dependency.  This may either be in the
    // gradle cache, or exist locally in "localArtifacts" (which we create for
    // those who may not have access to the artifact repository).  May return null.
    private File getIvyFile(
        Configuration conf,
        ResolvedDependency resolvedDependency
    ) {
        project.logger.debug "getIvyFile(${project}, ${conf}, ${resolvedDependency})"

        IvyState ivyState = ivyStates[conf.name]
        if (ivyState == null) {
            Collection<Dependency> ivyDeps = getDependenciesForIvyFiles(
                conf.resolvedConfiguration.firstLevelModuleDependencies
            )
            Configuration ivyConf = project.configurations.detachedConfiguration(*ivyDeps)
            ivyState = new IvyState(ivyConf)
            collectResolvedIvyFiles(ivyState, ivyConf.resolvedConfiguration.firstLevelModuleDependencies)
            //noinspection GroovyAssignabilityCheck
            ivyStates[conf.name] = ivyState
        }

        return ivyState.ivyFiles[resolvedDependency.module.id]
    }

    private void traverseResolvedDependencies(
        Configuration conf,
        Collection<PackedDependencyHandler> packedDependencies,
        Collection<UnpackModule> unpackModules,
        Set<ResolvedDependency> dependencies
    ) {
        dependencies.each { resolvedDependency ->
            ModuleVersionIdentifier moduleVersion = resolvedDependency.getModule().getId()
            String moduleGroup = moduleVersion.getGroup()
            String moduleName = moduleVersion.getName()
            String versionStr = moduleVersion.getVersion()

            project.logger.debug "Under ${conf.name}, " +
                        "resolving ${moduleVersion.group}:${moduleVersion.name}:${moduleVersion.version} " +
                        "with conf ${resolvedDependency.configuration}"

            // Is there an ivy file corresponding to this dependency?
            File ivyFile = getIvyFile(conf, resolvedDependency)
            if (ivyFile != null && ivyFile.exists()) {
                println "ivy file = ${ivyFile}"

                // If we found an ivy file then just assume that we should be unpacking it.
                // Ideally we should look for a custom XML tag that indicates that it was published
                // by the intrepid plugin. However, that would break compatibility with lots of
                // artifacts that have already been published.

                // Find or create an UnpackModule instance.
                UnpackModule unpackModule = unpackModules.find { it.matches(moduleVersion) }
                if (unpackModule == null) {
                    unpackModule = new UnpackModule(moduleGroup, moduleName)
                    unpackModules << unpackModule
                }

                // Find a parent UnpackModuleVersion instance i.e. one which has a dependency on
                // 'this' UnpackModuleVersion. There will only be a parent if this is a transitive
                // dependency. TODO: There could be more than one parent. Deal with it gracefully.
                UnpackModuleVersion parentUnpackModuleVersion = null
                resolvedDependency.getParents().each { parentDependency ->
                    ModuleVersionIdentifier parentDependencyVersion = parentDependency.getModule().getId()
                    UnpackModule parentUnpackModule = unpackModules.find { it.matches(parentDependencyVersion) }
                    if (parentUnpackModule != null) {
                        parentUnpackModuleVersion = parentUnpackModule.getVersion(parentDependencyVersion)
                    }
                }

                // Find or create an UnpackModuleVersion instance.
                UnpackModuleVersion unpackModuleVersion
                if (unpackModule.versions.containsKey(versionStr)) {
                    unpackModuleVersion = unpackModule.versions[versionStr]
                } else {

                    // If this resolved dependency is a transitive dependency, "thisPackedDep"
                    // below will be null
                    PackedDependencyHandler thisPackedDep = packedDependencies.find {
                        it.getDependencyName() == moduleName
                    }

                    unpackModuleVersion = new UnpackModuleVersion(moduleVersion, ivyFile, parentUnpackModuleVersion, thisPackedDep)
                    unpackModule.versions[versionStr] = unpackModuleVersion
                }

                unpackModuleVersion.addArtifacts(resolvedDependency.getModuleArtifacts(), conf.name)

                // Recurse down to transitive dependencies.
                traverseResolvedDependencies(
                    conf, packedDependencies, unpackModules, resolvedDependency.getChildren()
                )
            }
        }
    }

    public getAllUnpackModules() {
        if (unpackModules == null) {
            final packedDependencies = project.packedDependencies as Collection<PackedDependencyHandler>

            // Build a list (without duplicates) of all artifacts the project depends on.
            unpackModules = []
            project.configurations.each { conf ->
                ResolvedConfiguration resConf = conf.resolvedConfiguration
                traverseResolvedDependencies(
                    conf,
                    packedDependencies,
                    unpackModules,
                    resConf.getFirstLevelModuleDependencies()
                )
            }

            // Check if we have artifacts for each entry in packedDependency.
            if (!project.gradle.startParameter.isOffline()) {
                println "unpackModules = ${unpackModules}"
                boolean fail = false
                packedDependencies.each { dep ->
                    if (!unpackModules.any { it.name == dep.getDependencyName() }) {
                        //throw new RuntimeException(
                        println(
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
            Map<File, Collection<String>> targetLocations = [:]
            unpackModules.each { UnpackModule module ->
                module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                    File targetPath = versionInfo.getTargetPathInWorkspace(project).getCanonicalFile()
                    if (targetLocations.containsKey(targetPath)) {
                        targetLocations[targetPath].add(versionInfo.getFullCoordinate())
                    } else {
                        targetLocations[targetPath] = [versionInfo.getFullCoordinate()]
                    }
                }

                if (module.versions.size() > 1) {
                    int noIncludesCount = 0
                    module.versions.any { String versionStr, UnpackModuleVersion versionInfo ->
                        !versionInfo.includeVersionNumberInPath
                    }
                    if (noIncludesCount > 0) {
                        print "Dependencies have been detected on different versions of the module '${module.name}'. "
                        print "To prevent different versions of this module being unpacked to the same location, the version number will be "
                        print "appended to the path as '${module.name}-<version>'. You can make this warning disappear by changing the locations "
                        print "to which these dependencies are being unpacked. "
                        println "For your information, here are the details of the affected dependencies:"
                        module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                            print "  ${module.group}:${module.name}:${versionStr} : " + versionInfo.getIncludeInfo() + " -> "
                            versionInfo.includeVersionNumberInPath = true
                            println versionInfo.getIncludeInfo()
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
        }

        unpackModules
    }
}

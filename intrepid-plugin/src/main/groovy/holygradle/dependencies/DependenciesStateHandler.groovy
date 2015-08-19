package holygradle.dependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.logging.Logger
import org.gradle.api.specs.Specs

/**
 * This project extension provides information about resolved dependencies for a project or a project's buildscript.
 *
 * Specifically, it provides access to the metadata (Ivy XML and Maven POM) files for the dependencies.
 */
class DependenciesStateHandler {
    // Map of "original configuration name" -> "original module ID" -> ivy file location
    private final Map<String, Map<ModuleVersionIdentifier, File>> ivyFileMaps =
        new HashMap<String, Map<ModuleVersionIdentifier, File>>()
    private final Map<String, Map<ModuleVersionIdentifier, File>> pomFileMaps =
        new HashMap<String, Map<ModuleVersionIdentifier, File>>()

    private final Project project
    private final boolean forBuildscript
    private final Logger logger

    /**
     * Adds an instance of {@link DependenciesStateHandler} as an extendion to the given project.
     *
     * @param project The project to which this extension instance applies.
     * @param forBuildscript True if this handler is for the dependencies of a project's buildscript, in which case it
     * is added as "buildscriptDependenciesState"; false if it is for the dependencies of the project itself, in which
     * case it is added as "dependenciesState".
     * @return The created handler
     */
    public static DependenciesStateHandler createExtension(Project project, boolean forBuildscript = false) {
        if (forBuildscript) {
            project.extensions.buildscriptDependenciesState = new DependenciesStateHandler(project, forBuildscript)
            project.extensions.buildscriptDependenciesState
        } else {
            project.extensions.dependenciesState = new DependenciesStateHandler(project, forBuildscript)
            project.extensions.dependenciesState
        }
    }

    /**
     * Creates an instance of {@link DependenciesStateHandler} for the given project.
     *
     * @param project The project to which this extension instance applies.
     * @param forBuildscript True if this handler is for the dependencies of a project's buildscript; false if it is for
     * the dependencies of the project itself.
     */
    public DependenciesStateHandler(Project project, boolean forBuildscript = false) {
        this.project = project
        this.forBuildscript = forBuildscript
        this.logger = project.logger
    }

    /**
     * Copies the original {@code configuration}, ignoring its super-configurations and omitting all its dependencies,
     * then adding all the given {@code dependencies} -- thus keeping any other @{link Configuration} values such as the
     * @{link Configuration#resolutionStrategy}.
     * @param configuration
     * @param dependencies
     * @return A copy of the configuration with replaced dependencies (and ignoring the original's super-configurations).
     */
    public static Configuration copyConfigurationReplacingDependencies(
        Configuration configuration,
        Dependency... dependencies
    ) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.

        // Copy the original
        Configuration newConfiguration = configuration.copy(Specs.convertClosureToSpec { false })
        newConfiguration.dependencies.addAll(dependencies)
        newConfiguration
    }

    /**
     * Returns true if and only if the given module version id matches one of the modules being built by the current
     * build.
     * @param id The id of the module to consider
     * @return True iff the module is one in this build.
     */
    public boolean isModuleInBuild(ModuleVersionIdentifier id) {
        if (forBuildscript) {
            return false
        }
        
        final Project moduleInBuild = project.rootProject.allprojects.find {
            (it.group == id.group) &&
                (it.name == id.name) &&
                (it.version == id.version)
        }
        return moduleInBuild != null
    }

    /**
     * Given a set of resolved dependencies, returns a collection of unresolved dependencies for the metadata files
     * ({@code ivy.xml} and {@code *.pom}) corresponding to the resolved modules.  This allows us to find those files on
     * disk (to read custom parts of the XML), even though Gradle doesn't normally expose their location.  Note that
     * some dependencies might not have any metadata file.
     *
     * @param dependencies A set of resolved dependencies.
     * @return A set of unresolved dependencies for the corresponding Ivy and POM files.
     */
    private Collection<Dependency> getDependenciesForMetadataFiles(
        Set<ResolvedDependency> dependencies
    ) {
        // In a way it would make more sense to resolve configurations for Ivy files and POM files separately, because
        // for Ivy we just need the given files, whereas for POM we need to recurse to get any parent POMs.  However,
        // in Gradle 1.4, resolving configurations is slow, so we lump them together to reduce the number of
        // configurations we need to resolve.  Also, we may have configurations with a mix of Ivy and POM dependencies
        // so, even if there are no POM parents, we can save time by doing both together.

        Map<ModuleVersionIdentifier, Dependency> result = [:]
        // Track which "module version with configuration"s we've seen, to avoid visiting dependencies more than needed.
        Set<ResolvedDependenciesVisitor.ResolvedDependencyId> dependencyConfigurationsAlreadySeen = new HashSet<ResolvedDependenciesVisitor.ResolvedDependencyId>()

        ResolvedDependenciesVisitor.traverseResolvedDependencies(
            dependencies,
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id
                final boolean dependencyAlreadySeen = result.containsKey(id)
                logger.debug "getDependenciesForMetadataFiles: Considering dep ${id}: dependencyAlreadySeen == ${dependencyAlreadySeen}"
                final boolean moduleIsInBuild = isModuleInBuild(id)
                logger.debug "getDependenciesForMetadataFiles: Considering dep ${id}: moduleIsInBuild == ${moduleIsInBuild}"
                final boolean dependencyConfigurationAlreadySeen = !dependencyConfigurationsAlreadySeen.add(
                    new ResolvedDependenciesVisitor.ResolvedDependencyId(id, resolvedDependency.configuration)
                )
                logger.debug "getDependenciesForMetadataFiles: Considering dep ${id}: dependencyConfigurationAlreadySeen == ${dependencyConfigurationAlreadySeen}"
                return new ResolvedDependenciesVisitor.VisitChoice(
                    // Visit this dependency only if we've haven't seen it already, and it's not a module we're building from
                    // source (because we don't need an ivy file for that -- we have relative path info in the source dep).
                    !dependencyAlreadySeen && !moduleIsInBuild,
                    // Visit this dependency's children only if we've haven't seen it already.  We still want to search into
                    // modules which are part of the source for this build, so we don't call isModuleInBuild.
                    !dependencyConfigurationAlreadySeen
                )
            },
            { ResolvedDependency resolvedDependency ->
                ModuleVersionIdentifier id = resolvedDependency.module.id

                // Create a dependency which has artifacts which would match either the appropriate Ivy XML or Maven POM
                // file.  The dependency will be used with a "lenient" configuration, so it's okay that they won't both
                // exist.
                ExternalModuleDependency dep = new DefaultExternalModuleDependency(
                    id.group, id.name, id.version, resolvedDependency.configuration
                )
                dep.artifact { art ->
                    art.name = "ivy"
                    art.type = "ivy"
                    art.extension = "xml"
                }
                dep.artifact { art ->
                    art.name = id.name
                    art.type = "pom"
                    art.extension = "pom"
                }
                result[id] = dep
                logger.debug "getDependenciesForMetadataFiles: Added ${dep} for ${id}"
            }
        )

        return new ArrayList(result.values())
    }

    /**
     * Given a set of POM files, returns a collection of unresolved dependencies for the modules which should contain
     * the corresponding parent POM files, if any.  (A POM file can have zero or one parent.)  These modules must then
     * be resolved to locate the parent POM files.
     *
     * @param pomFiles A set of POM files.
     * @return A set of unresolved dependencies for any corresponding parent POM modules.
     */
    private Collection<Dependency> getParentDependenciesForPomFiles(
        Collection<File> pomFiles
    ) {
        Map<ModuleVersionIdentifier, Dependency> result = [:]

        pomFiles.each { File pomFile ->
            // NOTE 2013-07-12 HughG: No point declaring the type for a GPathResult, because almost all access to
            // such objects uses magic dynamic properties.
            def /* GPathResult */ pomXml

            try {
                pomXml = new XmlSlurper(false, false).parseText(pomFile.text)
            } catch (Exception e) {
                logger.warn("getParentDependenciesForPomFiles: Ignoring error while parsing XML in ${pomFile}: " + e.toString())
                return
            }

            // NOTE 2013-07-12 HughG: Suppressing an IntelliJ IDEA warning here, because "pomXml.parent" isn't supposed
            // to be accessing the protected "parent" property of the GPathResult class, but the GPath object for the
            // "<parent />" note in the "pom.xml".  IntelliJ figures out that this is a GPathResult even if we declare
            // it with "def", so we still need to suppress this.
            //noinspection GroovyAccessibility
            pomXml.parent.each { /* GPathResult */ parentNode ->
                String parentGroup = parentNode.groupId.text()
                String parentName = parentNode.artifactId.text()
                String parentVersion = parentNode.version.text()
                DefaultModuleVersionIdentifier parentId =
                    new DefaultModuleVersionIdentifier(parentGroup, parentName, parentVersion)

                String parentPomRelativePath = parentNode.relativePath.text()
                if (parentPomRelativePath != null && !parentPomRelativePath.isEmpty()) {
                    logger.info("getParentDependenciesForPomFiles: Ignoring relative path for '${parentId}': ${parentPomRelativePath}")
                }

                ExternalModuleDependency parentDep =
                    new DefaultExternalModuleDependency(parentGroup, parentName, parentVersion)
                parentDep.artifact { art ->
                    art.name = parentId.name
                    art.type = "pom"
                    art.extension = "pom"
                }
                result[parentId] = parentDep

                logger.debug("getParentDependenciesForPomFiles: Added parent ${parentId} from ${pomFile}")
            }
        }

        return new ArrayList(result.values())
    }

    /**
     * Populates a map from module version ID to the file location (in the local Gradle cache) of the corresponding
     * {@code ivy.xml} or @{code .pom} file.
     *
     * @param dependencies A set of resolved dependencies which are expected to have artifacts for {@code ivy.xml} or
     * @{code .pom} files.
     * @return The map from IDs to files.
     */
    private Map<ModuleVersionIdentifier, File> getResolvedMetadataFilesOfType(
        Set<ResolvedArtifact> allResolvedArtifacts,
        String type
    ) {
        Map<ModuleVersionIdentifier, File> metadataFiles = new HashMap<ModuleVersionIdentifier, File>()
        Map<ModuleVersionIdentifier, File> missingMetadataFiles = new HashMap<ModuleVersionIdentifier, File>()

        allResolvedArtifacts.each { ResolvedArtifact resolvedArtifact ->
            if (resolvedArtifact.type != type) {
                return
            }

            final ModuleVersionIdentifier id = resolvedArtifact.moduleVersion.id
            final File metadataFile = resolvedArtifact.file
            if (metadataFile == null) {
                missingMetadataFiles[id] = metadataFile
                logger.error(
                    "getResolvedMetadataFilesOfType: Failed to find ${type} file for ${id} in artifact ${resolvedArtifact}"
                )
            }
            metadataFiles[id] = metadataFile
            logger.debug "getResolvedMetadataFilesOfType: Added ${metadataFile} for ${id}"
        }

        // This should never happen, but I'd like to fail early if it does.
        if (!missingMetadataFiles.isEmpty()) {
            throw new RuntimeException("getResolvedMetadataFilesOfType: Some metadata files did not exist on disk")
        }

        return metadataFiles
    }

    /**
     * This method takes the set of the POM files for the transitive closure of dependencies in the given
     * {@link Configuration} (as returned by {@link #getPomFilesForConfiguration(org.gradle.api.artifacts.Configuration)})
     * and returns the complete list of "ancestor" POM files (parents, parents of parents, etc.) for the original set.
     * The original POM files are not included in the returned map, only the ancestors.
     *
     * The same ancestor may appear more than once, with different versions.  This is because parent POMs are not meant
     * to be treated as dependencies to resolve.  Rather, they are treated as modifying the base POM files, which are
     * themselves used in dependency resolution.
     *
     * @param conf
     * @return
     */
    public Map<ModuleVersionIdentifier, File> getAncestorPomFiles(Configuration conf) {
        logger.debug "getAncestorPomFiles(${conf} in ${project})"

        // As the javadoc comment says, we don't want to treat the parent POMs as dependencies to be resolved, because
        // we might need multiple versions of the same POM, and Gradle doesn't let you have multiple versions of a
        // module (in the same configuration).  However, the only safe way to get the location of a POM file is to
        // resolve a dependency based on its ModuleVersionId.  (We used to search through the Gradle cache, but that can
        // return the wrong result if the same ID has been mapped to different POM files, either because they changed
        // over time or because they were resolved from different underlying repositories.)
        //
        // To solve this, we resolve each ancestor pom in its own configuration.

        final Map<ModuleVersionIdentifier, File> ancestorPomFiles = new HashMap<ModuleVersionIdentifier, File>()
        Map<ModuleVersionIdentifier, File> pomFiles = getPomFilesForConfiguration(conf)
        // "while(true) { if (...) break }" because Groovy has no "do { ... } while (...)"
        while (true) {
            Collection<Dependency> parentPomDeps = getParentDependenciesForPomFiles(pomFiles.values())

            final Map<ModuleVersionIdentifier, File> newParentPomFiles =
                parentPomDeps.collectEntries { Dependency parentPomDep ->
                    //noinspection GroovyAssignabilityCheck
                    Configuration parentPomConf = copyConfigurationReplacingDependencies(conf, parentPomDep)
                    final LenientConfiguration lenientConf = parentPomConf.resolvedConfiguration.lenientConfiguration
                    Set<ResolvedArtifact> allResolvedArtifacts =
                        lenientConf.getArtifacts(Specs.convertClosureToSpec { true })
                    Map<ModuleVersionIdentifier, File> parentPomFiles =
                        getResolvedMetadataFilesOfType(allResolvedArtifacts, "pom")
                    parentPomFiles
                }

            if (newParentPomFiles.isEmpty()) {
                break
            }

            ancestorPomFiles.putAll(newParentPomFiles)

            pomFiles = newParentPomFiles
        }

        logger.info("getAncestorPomFiles: Resolved ancestor pom files for ${conf} in ${project}: ${ancestorPomFiles.values().join('\r\n')}")

        return ancestorPomFiles
    }

    // Get the Ivy and POM files for a given configuration by
    //  (a) finding all the resolved modules for the configuration;
    //  (b) making a matching collection of unresolved dependencies with the same module versions, but with the ivy.xml
    // file as the only artifact;
    //  (c) resolving the latter collection, getting the lenient version, and pulling out the artifact locations.
    //
    // We can't just ask Gradle for the locations, because it doesn't expose them.  See the forum post at
    // http://forums.gradle.org/gradle/topics/how_to_get_hold_of_ivy_xml_file_of_dependency
    private void initializeMetadataFilesForConfiguration(Configuration conf) {
        logger.debug "initializeMetadataFilesForConfiguration(${conf} in ${project})"

        Map<ModuleVersionIdentifier, File> ivyFiles = ivyFileMaps[conf.name]
        Map<ModuleVersionIdentifier, File> pomFiles = pomFileMaps[conf.name]
        if (ivyFiles == null && pomFiles == null) {
            // Create a configuration which has metadata artifacts for all transitive dependencies in conf.
            Collection<Dependency> metadataDeps = getDependenciesForMetadataFiles(
                conf.resolvedConfiguration.firstLevelModuleDependencies
            )
            //noinspection GroovyAssignabilityCheck
            Configuration ivyConf = copyConfigurationReplacingDependencies(conf, *metadataDeps)
            final LenientConfiguration lenientConf = ivyConf.resolvedConfiguration.lenientConfiguration
            Set<ResolvedArtifact> allResolvedArtifacts = lenientConf.getArtifacts(Specs.convertClosureToSpec { true })

            ivyFiles = getResolvedMetadataFilesOfType(allResolvedArtifacts, "ivy")
            ivyFileMaps[conf.name] = ivyFiles
            logger.info(
                "initializeMetadataFilesForConfiguration: Resolved ivy files for ${conf} in ${project}: " +
                "${ivyFiles.entrySet().join('\r\n')}"
            )

            pomFiles = getResolvedMetadataFilesOfType(allResolvedArtifacts, "pom")
            pomFileMaps[conf.name] = pomFiles
            logger.info(
                "initializeMetadataFilesForConfiguration: Resolved pom files for ${conf} in ${project}: " +
                " ${pomFiles.entrySet().join('\r\n')}"
            )
        }
    }

    public Map<ModuleVersionIdentifier, File> getIvyFilesForConfiguration(Configuration conf) {
        initializeMetadataFilesForConfiguration(conf)
        return ivyFileMaps[conf.name]
    }

    public Map<ModuleVersionIdentifier, File> getPomFilesForConfiguration(Configuration conf) {
        initializeMetadataFilesForConfiguration(conf)
        return pomFileMaps[conf.name]
    }

    /**
     * Get the ivy file for the resolved dependency.  This may either be in the Gradle cache, or exist in a subdirectory
     * of the project directory, named {@link CollectDependenciesHelper#LOCAL_ARTIFACTS_DIR_NAME}, which we create for
     * developers who may not have access to the artifact repository.  May return null.
     */
    public File getIvyFile(
        Configuration conf,
        ResolvedDependency resolvedDependency
    ) {
        Map<ModuleVersionIdentifier, File> ivyFiles = getIvyFilesForConfiguration(conf)
        return ivyFiles[resolvedDependency.module.id]
    }
}

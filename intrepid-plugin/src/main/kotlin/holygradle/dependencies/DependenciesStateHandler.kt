package holygradle.dependencies

import holygradle.artifacts.ConfigurationHelper
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.specs.Specs
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * This project extension provides information about resolved dependencies for a project or a project's buildscript.
 *
 * Specifically, it provides access to the metadata (Ivy XML and Maven POM) files for the dependencies.
 */
open class DependenciesStateHandler
    /**
     * Creates an instance of {@link DependenciesStateHandler} for the given project.
     *
     * @param project The project to which this extension instance applies.
     * @param forBuildscript True if this handler is for the dependencies of a project's buildscript; false if it is for
     * the dependencies of the project itself.
     */
    constructor(
            private val project: Project,
            private val forBuildscript: Boolean
    )
{
    private class NodeListIterator(private val nodeList: NodeList): Iterator<Node> {
        private var index = 0

        override fun hasNext(): Boolean {
            return index <= nodeList.length
        }

        override fun next(): Node {
            return nodeList.item(index++)
        }
    }

    private class NodeListIterable(private val nodeList: NodeList): Iterable<Node> {
        override fun iterator(): Iterator<Node> {
            return NodeListIterator(nodeList)
        }
    }

    private fun NodeList.toIterable() = NodeListIterable(this)

    companion object {
        /**
         * Adds an instance of {@link DependenciesStateHandler} as an extendion to the given project.
         *
         * @param project The project to which this extension instance applies.
         * @param forBuildscript True if this handler is for the dependencies of a project's buildscript, in which case it
         * is added as "buildscriptDependenciesState"; false if it is for the dependencies of the project itself, in which
         * case it is added as "dependenciesState".
         * @return The created handler
         */
        @JvmStatic
        fun createExtension(project: Project, forBuildscript: Boolean = false): DependenciesStateHandler  {
            val handler = DependenciesStateHandler(project, forBuildscript)
            val handlerName = if (forBuildscript) "buildscriptDependenciesState" else "dependenciesState"
            project.extensions.add(handlerName, handler)
            return handler
        }

        /**
         * Copies the original {@code configuration}, ignoring its super-configurations and omitting all its dependencies,
         * then adding all the given {@code dependencies} -- thus keeping any other @{link Configuration} values such as the
         * @{link Configuration#resolutionStrategy}.
         * @param configuration
         * @param dependencies
         * @return A copy of the configuration with replaced dependencies (and ignoring the original's super-configurations).
         */
        private fun copyConfigurationReplacingDependencies(
                configuration: Configuration,
                dependencies: Collection<Dependency>
        ): Configuration {
            // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.

            // Copy the original
            val newConfiguration = configuration.copy(Specs.satisfyNone())
            newConfiguration.dependencies.addAll(dependencies)
            return newConfiguration
        }
    }

    // Map of "original configuration name" -> "original module ID" -> ivy file location
    private val ivyFileMaps: MutableMap<String, Map<ModuleVersionIdentifier, File>> = mutableMapOf()
    private val pomFileMaps: MutableMap<String, Map<ModuleVersionIdentifier, File>> = mutableMapOf()

    private val logger = project.logger

    /**
     * Returns true if and only if the given module version id matches one of the modules being built by the current
     * build.
     * @param id The id of the module to consider
     * @return True iff the module is one in this build.
     */
    fun isModuleInBuild(id: ModuleVersionIdentifier): Boolean {
        if (forBuildscript) {
            return false
        }
        
        val moduleInBuild = project.rootProject.allprojects.find {
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
    private fun getDependenciesForMetadataFiles(
            dependencies: Set<ResolvedDependency>
    ): Collection<Dependency> {
        // In a way it would make more sense to resolve configurations for Ivy files and POM files separately, because
        // for Ivy we just need the given files, whereas for POM we need to recurse to get any parent POMs.  However,
        // in Gradle 1.4, resolving configurations is slow, so we lump them together to reduce the number of
        // configurations we need to resolve.  Also, we may have configurations with a mix of Ivy and POM dependencies
        // so, even if there are no POM parents, we can save time by doing both together.

        val result = mutableMapOf<ModuleVersionIdentifier, Dependency>()
        // Track which "module version with configuration"s we've seen, to avoid visiting dependencies more than needed.
        val dependencyConfigurationsAlreadySeen = mutableSetOf<ResolvedDependenciesVisitor.ResolvedDependencyId>()

        ResolvedDependenciesVisitor.traverseResolvedDependencies(
            dependencies,
            { resolvedDependency ->
                val id = resolvedDependency.module.id
                val dependencyAlreadySeen = result.containsKey(id)
                logger.debug("getDependenciesForMetadataFiles: Considering dep ${id}: dependencyAlreadySeen == ${dependencyAlreadySeen}")
                val moduleIsInBuild = isModuleInBuild(id)
                logger.debug("getDependenciesForMetadataFiles: Considering dep ${id}: moduleIsInBuild == ${moduleIsInBuild}")
                val dependencyConfigurationAlreadySeen = !dependencyConfigurationsAlreadySeen.add(
                    ResolvedDependenciesVisitor.ResolvedDependencyId(id, resolvedDependency.configuration)
                )
                logger.debug("getDependenciesForMetadataFiles: Considering dep ${id}: dependencyConfigurationAlreadySeen == ${dependencyConfigurationAlreadySeen}")
                ResolvedDependenciesVisitor.VisitChoice(
                    // Visit this dependency only if we've haven't seen it already, and it's not a module we're building from
                    // source (because we don't need an ivy file for that -- we have relative path info in the source dep).
                    !dependencyAlreadySeen && !moduleIsInBuild,
                    // Visit this dependency's children only if we've haven't seen it already.  We still want to search into
                    // modules which are part of the source for this build, so we don't call isModuleInBuild.
                    !dependencyConfigurationAlreadySeen
                )
            },
            { resolvedDependency ->
                val id = resolvedDependency.module.id

                // Create a dependency which has artifacts which would match either the appropriate Ivy XML or Maven POM
                // file.  The dependency will be used with a "lenient" configuration, so it's okay that they won't both
                // exist.
                val dep = DefaultExternalModuleDependency(
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
                logger.debug("getDependenciesForMetadataFiles: Added ${dep} for ${id}")
            }
        )

        return result.values.toList()
    }

    /**
     * Given a set of POM files, returns a collection of unresolved dependencies for the modules which should contain
     * the corresponding parent POM files, if any.  (A POM file can have zero or one parent.)  These modules must then
     * be resolved to locate the parent POM files.
     *
     * @param pomFiles A set of POM files.
     * @return A set of unresolved dependencies for any corresponding parent POM modules.
     */
    private fun getParentDependenciesForPomFiles(
        pomFiles: Collection<File>
    ): Collection<Dependency> {
        val result = mutableMapOf<ModuleVersionIdentifier, Dependency>()

        for (pomFile in pomFiles) {
            val xmlDoc: Document = try {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
            } catch (e: Exception) {
                logger.warn("WARNING: getParentDependenciesForPomFiles: Ignoring error while parsing XML in ${pomFile}: ${e}")
                continue
            }
            xmlDoc.documentElement.normalize()
            val parents = xmlDoc.documentElement.childNodes.toIterable().filterIsInstance<Element>().filter {
                it.tagName == "parent"
            }
            for (parentNode in parents) {
                val parentGroup = parentNode.getElementsByTagName("groupId").item(0).textContent
                val parentName = parentNode.getElementsByTagName("artifactId").item(0).textContent
                val parentVersion = parentNode.getElementsByTagName("version").item(0).textContent
                val parentId = DefaultModuleVersionIdentifier(parentGroup, parentName, parentVersion)

                val parentPomRelativePathNodes = parentNode.getElementsByTagName("relativePath")
                if (parentPomRelativePathNodes.length > 0) {
                    val parentPomRelativePath = parentPomRelativePathNodes.item(0).textContent
                    if (!parentPomRelativePath.isEmpty()) {
                        logger.info("getParentDependenciesForPomFiles: Ignoring relative path for '${parentId}': ${parentPomRelativePath}")
                    }
                }

                val parentDep = DefaultExternalModuleDependency(parentGroup, parentName, parentVersion)
                parentDep.artifact { art ->
                    art.name = parentId.name
                    art.type = "pom"
                    art.extension = "pom"
                }
                result[parentId] = parentDep

                logger.debug("getParentDependenciesForPomFiles: Added parent ${parentId} from ${pomFile}")
            }
        }

        return result.values.toList()
    }

    /**
     * Populates a map from module version ID to the file location (in the local Gradle cache) of the corresponding
     * {@code ivy.xml} or @{code .pom} file.
     *
     * @param allResolvedArtifacts A set of resolved artifacts for {@code ivy.xml} or @{code .pom} files.
     * @param type The Gradle artifact type as a string, "ivy" or "pom".
     * @return The map from IDs to files.
     */
    private fun getResolvedMetadataFilesOfType(
        allResolvedArtifacts: Set<ResolvedArtifact>,
        type: String
    ): Map<ModuleVersionIdentifier, File>  {
        val metadataFiles = mutableMapOf<ModuleVersionIdentifier, File>()
        val missingMetadataFiles = mutableMapOf<ModuleVersionIdentifier, File>()

        for (resolvedArtifact in allResolvedArtifacts) {
            if (resolvedArtifact.type != type) {
                continue
            }

            val id = resolvedArtifact.moduleVersion.id
            val metadataFile = resolvedArtifact.file
            metadataFiles[id] = metadataFile
            logger.debug("getResolvedMetadataFilesOfType: Added ${metadataFile} for ${id}")
        }

        // This should never happen, but I'd like to fail early if it does.
        if (!missingMetadataFiles.isEmpty()) {
            throw RuntimeException("getResolvedMetadataFilesOfType: Some metadata files did not exist on disk")
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
    fun getAncestorPomFiles(conf: Configuration): Map<ModuleVersionIdentifier, File> {
        logger.debug("getAncestorPomFiles(${conf} in ${project})")

        // As the javadoc comment says, we don't want to treat the parent POMs as dependencies to be resolved, because
        // we might need multiple versions of the same POM, and Gradle doesn't let you have multiple versions of a
        // module (in the same configuration).  However, the only safe way to get the location of a POM file is to
        // resolve a dependency based on its ModuleVersionId.  (We used to search through the Gradle cache, but that can
        // return the wrong result if the same ID has been mapped to different POM files, either because they changed
        // over time or because they were resolved from different underlying repositories.)
        //
        // To solve this, we resolve each ancestor pom in its own configuration.

        val ancestorPomFiles = mutableMapOf<ModuleVersionIdentifier, File>()
        var pomFiles = getPomFilesForConfiguration(conf)
        // "while(true) { if (...) break }" because Groovy has no "do { ... } while (...)"
        while (true) {
            val parentPomDeps = getParentDependenciesForPomFiles(pomFiles.values)

            val newParentPomFiles: Map<ModuleVersionIdentifier, File> =
                parentPomDeps.associate { parentPomDep ->
                    val parentPomConf = copyConfigurationReplacingDependencies(conf, listOf(parentPomDep))
                    val lenientConf = parentPomConf.resolvedConfiguration.lenientConfiguration
                    val allResolvedArtifacts = lenientConf.getArtifacts(Specs.satisfyAll())
                    return getResolvedMetadataFilesOfType(allResolvedArtifacts, "pom")
                }

            if (newParentPomFiles.isEmpty()) {
                break
            }

            ancestorPomFiles.putAll(newParentPomFiles)

            pomFiles = newParentPomFiles
        }

        val ancestorPomFilesString = ancestorPomFiles.values.joinToString("\r\n")
        logger.info("getAncestorPomFiles: Resolved ancestor pom files for ${conf} in ${project}: $ancestorPomFilesString")

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
    private fun initializeMetadataFilesForConfiguration(conf: Configuration) {
        logger.debug("initializeMetadataFilesForConfiguration(${conf} in ${project})")

        var ivyFiles = ivyFileMaps[conf.name]
        var pomFiles = pomFileMaps[conf.name]
        if (ivyFiles == null && pomFiles == null) {
            // Create a configuration which has metadata artifacts for all transitive dependencies in conf.
            val metadataDeps = getDependenciesForMetadataFiles(
                ConfigurationHelper.getFirstLevelModuleDependenciesForMaybeOptionalConfiguration(conf)
            )
            //noinspection GroovyAssignabilityCheck
            val ivyConf = copyConfigurationReplacingDependencies(conf, metadataDeps)
            val lenientConf = ivyConf.resolvedConfiguration.lenientConfiguration
            val allResolvedArtifacts = lenientConf.getArtifacts(Specs.satisfyAll())

            ivyFiles = getResolvedMetadataFilesOfType(allResolvedArtifacts, "ivy")
            ivyFileMaps[conf.name] = ivyFiles
            logger.info(
                "initializeMetadataFilesForConfiguration: Resolved ivy files for ${conf} in ${project}: " +
                        ivyFiles.entries.joinToString("\r\n")
            )

            pomFiles = getResolvedMetadataFilesOfType(allResolvedArtifacts, "pom")
            pomFileMaps[conf.name] = pomFiles
            logger.info(
                "initializeMetadataFilesForConfiguration: Resolved pom files for ${conf} in ${project}: " +
                        pomFiles.entries.joinToString("\r\n")
            )
        }
    }

    fun getIvyFilesForConfiguration(conf: Configuration): Map<ModuleVersionIdentifier, File> {
        checkConfigurationIsInProject(conf)
        initializeMetadataFilesForConfiguration(conf)
        return ivyFileMaps[conf.name] ?: throw RuntimeException("Failed to initialize ivy file information for ${conf}")
    }

    fun getPomFilesForConfiguration(conf: Configuration): Map<ModuleVersionIdentifier, File> {
        checkConfigurationIsInProject(conf)
        initializeMetadataFilesForConfiguration(conf)
        return pomFileMaps[conf.name] ?: throw RuntimeException("Failed to initialize pom file information for ${conf}")
    }

    //
    /**
     * Check that the configuration really belongs to this project.  If not -- that is, if we're given a configuration
     * from another project, we risk collecting state which is wrong for this project.  If we didn't do this check, and
     * were later asked to get the state for the same-named configuration in this project, we'd return that wrong info.
     * @param conf
     */
    private fun checkConfigurationIsInProject(conf: Configuration) {
        val projectConfWithName = project.configurations.findByName(conf.name)
        val buildscriptConfWithName = project.buildscript.configurations.findByName(conf.name)
        if (projectConfWithName != conf && buildscriptConfWithName != conf) {
            throw RuntimeException("${conf} is not from ${project} or its buildscript")
        }
    }
}

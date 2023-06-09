package holygradle.source_dependencies

import holygradle.Helper
import holygradle.IntrepidPlugin
import holygradle.custom_gradle.plugin_apis.DEFAULT_CREDENTIAL_TYPE
import holygradle.custom_gradle.util.CamelCase
import holygradle.dependencies.DependencyHandler
import holygradle.kotlin.dsl.container
import holygradle.kotlin.dsl.newInstance
import holygradle.scm.CommandLine
import holygradle.scm.GitDependency
import holygradle.scm.HgDependency
import holygradle.scm.SvnDependency
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import holygradle.kotlin.dsl.project
import holygradle.kotlin.dsl.task
import java.io.File
import javax.inject.Inject

open class SourceDependencyHandler @Inject constructor (
        depName: String,
        project: Project
) : DependencyHandler(depName, project) {
    companion object {
        @JvmStatic
        val EVERYTHING_CONFIGURATION_MAPPING: Map.Entry<String, String> =
                java.util.AbstractMap.SimpleEntry<String, String>(
                        IntrepidPlugin.EVERYTHING_CONFIGURATION_NAME, IntrepidPlugin.EVERYTHING_CONFIGURATION_NAME
                )

        @JvmStatic
        fun createContainer(project: Project): Collection<SourceDependencyHandler> {
            val sourceDependencies =
                    project.container<SourceDependencyHandler> { sourceDepName: String ->
                        project.objects.newInstance(sourceDepName, project)
                    }
            project.extensions.add("sourceDependencies", sourceDependencies)
            return sourceDependencies
        }
    }

    lateinit var protocol: String
    lateinit var url: String
    var branch: String? = null
    val credentialBasis = DEFAULT_CREDENTIAL_TYPE
    val writeVersionInfoFile: Boolean? = null
    var export: Boolean = false
    val destinationDir: File = absolutePath.canonicalFile

    init {
        project.afterEvaluate { validate() }
    }

    fun hg(hgUrl: String) {
        if (::protocol.isInitialized || ::url.isInitialized) {
            throw RuntimeException("Cannot call 'hg' when protocol and/or url has already been set")
        } else {
            protocol = "hg"
            url = hgUrl
        }
    }
    
    fun svn(svnUrl: String) {
        if (::protocol.isInitialized || ::url.isInitialized) {
            throw RuntimeException("Cannot call 'svn' when protocol and/or url has already been set")
        } else {
            protocol = "svn"
            url = svnUrl
        }
    }

    fun git(gitUrl: String) {
        if (::protocol.isInitialized || ::url.isInitialized) {
            throw RuntimeException("Cannot call 'git' when protocol and/or url has already been set")
        } else {
            protocol = "git"
            url = gitUrl
        }
    }

    override fun configuration(config: String) {
        val newConfigs = mutableListOf<Map.Entry<String, String>>()
        Helper.parseConfigurationMapping(config, newConfigs, "Formatting error for '$targetName' in 'sourceDependencies'.")
        configurationMappings.addAll(newConfigs)

        val rootProject = project.rootProject
        val depProject = rootProject.findProject(":${targetName}")
        if (depProject == null) {
            if (!newConfigs.isEmpty()) {
                project.logger.info(
                    "Not creating project dependencies from ${project.name} on ${targetName}, " +
                    "because ${this.targetName} has no project file."
                )
            }
        } else if (!depProject.projectDir.exists()) {
            if (!newConfigs.isEmpty()) {
                project.logger.info(
                    "Not creating project dependencies from ${project.name} on ${targetName}, " +
                    "because ${this.targetName} has yet to be fetched."
                )
            }
        } else {
            for ((fromConf, toConf) in newConfigs) {
                project.logger.info(
                    "sourceDependencies: adding project dependency " +
                    "from ${project.group}:${project.name} conf=${fromConf} " +
                    "on :${targetName} conf=${toConf}"
                )
                project.dependencies.add(
                    fromConf,
                    rootProject.dependencies.project(path = ":${targetName}", configuration = toConf)
                )
            }
        }

        // Add a mapping for "everything" lazily, i.e., only if we add any other mappings.  This makes it easy to have
        // un-published source dependencies, e.g., to just pull in some repo containing documentation.
        if (project.hasProperty(IntrepidPlugin.EVERYTHING_CONFIGURATION_PROPERTY) &&
            !configurationMappings.contains(EVERYTHING_CONFIGURATION_MAPPING)
        ) {
            configuration(IntrepidPlugin.EVERYTHING_CONFIGURATION_NAME)
        }
    }

    fun createFetchTask(project: Project): Task  {
        val sourceDependency: SourceDependency = if (protocol == "svn") {
            val hgCommand = CommandLine(project.logger, "svn.exe", project::exec)
            SvnDependency(project,this, hgCommand)
        } else if (protocol == "hg") {
            val hgCommand = CommandLine(project.logger, "hg.exe", project::exec)
            HgDependency(project, this, hgCommand)
        } else if (protocol == "git") {
            val gitCommand = CommandLine(project.logger, "git.exe", project::exec)
            GitDependency(project, this, gitCommand)
        } else {
            throw RuntimeException("Unsupported protocol: " + protocol)
        }
        val fetchTaskName = CamelCase.build("fetch", targetName)
        return project.task<FetchSourceDependencyTask>(fetchTaskName).apply {
            description = sourceDependency.fetchTaskDescription
            initialize(sourceDependency)
        }
    }

    /**
     * Returns the module version ID of the source dependency, unless there is no configuration mapping to it (in which
     * case returns {@code null}), or unless it has configuration mappings doesn't have a Gradle project which is
     * included in the build (in which case throws RuntimeException).
     * @return The module version ID of the source dependency.
     * @throws RuntimeException if the source dependency has configuration mappings but does not have a Gradle project.
     */
    val dependencyId: ModuleVersionIdentifier?
        get() {
            var identifier: ModuleVersionIdentifier? = null
            if (configurationMappings.isNotEmpty()) {
                val dependencyProject: Project = project.rootProject.findProject(targetName)
                        // NOTE HughG: I've made this mistake a few times when modifying the settings.gradle code, so I put
                        // in this explicit check and helpful message.
                        ?: throw RuntimeException(
                        "Internal error: failed to find project '${targetName}'; " +
                                "maybe name override in settings.gradle was not done correctly."
                )
                val groupName = dependencyProject.group.toString()
                val version = dependencyProject.version.toString()

                identifier = DefaultModuleVersionIdentifier.newId(groupName, targetName, version)
            }
            return identifier
        }

    /**
     * Returns the module version ID of the source dependency as a string, unless there is no configuration mapping to
     * it (in which case returns {@code null}), or unless it has configuration mappings doesn't have a Gradle project
     * which is included in the build (in which case throws RuntimeException).
     * @return The module version ID string of the source dependency.
     * @throws RuntimeException if the source dependency has configuration mappings but does not have a Gradle project.
     */
    val dependencyCoordinate: String? get() = dependencyId?.toString()

    val sourceDependencyProject: Project? get() = project.rootProject.findProject(targetName)

    private fun validate() {
        val dependencyProject = project.rootProject.findProject(targetName)
        if (configurationMappings.isEmpty()) {
            if (dependencyProject == null) {
                project.logger.debug("${project} has a source dependency on ${name}, which is not a Gradle project " +
                        "in this build -- it is a non-Gradle source checkout")
            } else {
                project.logger.warn("WARNING: ${project} has a source dependency on ${name}, which is a project in " +
                        "this build, but there is no configuration mapping.  This is probably a mistake.  " +
                        "It means that, when ${project.name} is published, there will be no real dependency on ${targetName}.")
            }
        }
    }
}

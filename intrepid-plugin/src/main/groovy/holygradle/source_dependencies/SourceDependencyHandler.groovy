package holygradle.source_dependencies

import holygradle.Helper
import holygradle.IntrepidPlugin
import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.custom_gradle.util.CamelCase
import holygradle.dependencies.DependencyHandler
import holygradle.scm.CommandLine
import holygradle.scm.GitDependency
import holygradle.scm.HgDependency
import holygradle.scm.SvnDependency
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

class SourceDependencyHandler extends DependencyHandler {
    private static final AbstractMap.SimpleEntry<String, String> EVERYTHING_CONFIGURATION_MAPPING =
        new AbstractMap.SimpleEntry<String, String>(
            IntrepidPlugin.EVERYTHING_CONFIGURATION_NAME, IntrepidPlugin.EVERYTHING_CONFIGURATION_NAME
        )
    public String protocol = null
    public String url = null
    public String branch = null
    public String credentialBasis = CredentialSource.DEFAULT_CREDENTIAL_TYPE
    public Boolean writeVersionInfoFile = null
    public boolean export = false
    private File destinationDir

    public static Collection<SourceDependencyHandler> createContainer(Project project) {
        project.extensions.sourceDependencies = project.container(SourceDependencyHandler) { String sourceDepName ->
            new SourceDependencyHandler(sourceDepName, project)
        }
        project.sourceDependencies as Collection<SourceDependencyHandler>
    }

    public SourceDependencyHandler(String depName, Project project) {
        super(depName, project)
        destinationDir = absolutePath.canonicalFile
        project.afterEvaluate { validate() }
    }

    public File getDestinationDir() {
        destinationDir
    }
    
    public void hg(String hgUrl) {
        if (protocol != null || url != null) {
            throw new RuntimeException("Cannot call 'hg' when protocol and/or url has already been set")
        } else {
            protocol = "hg"
            url = hgUrl
        }
    }
    
    public void svn(String svnUrl) {
        if (protocol != null || url != null) {
            throw new RuntimeException("Cannot call 'svn' when protocol and/or url has already been set")
        } else {
            protocol = "svn"
            url = svnUrl
        }
    }

    public void git(String gitUrl) {
        if (protocol != null || url != null) {
            throw new RuntimeException("Cannot call 'git' when protocol and/or url has already been set")
        } else {
            protocol = "git"
            url = gitUrl
        }
    }

    public void configuration(String config) {
        Collection<AbstractMap.SimpleEntry<String, String>> newConfigs = []
        Helper.parseConfigurationMapping(config, newConfigs, "Formatting error for '$targetName' in 'sourceDependencies'.")
        configurationMappings.addAll(newConfigs)

        Project rootProject = project.rootProject
        Project depProject = rootProject.findProject(":${targetName}")
        if (depProject == null) {
            if (!newConfigs.empty) {
                project.logger.info(
                    "Not creating project dependencies from ${project.name} on ${targetName}, " +
                    "because ${this.targetName} has no project file."
                )
            }
        } else if (!depProject.projectDir.exists()) {
            if (!newConfigs.empty) {
                project.logger.info(
                    "Not creating project dependencies from ${project.name} on ${targetName}, " +
                    "because ${this.targetName} has yet to be fetched."
                )
            }
        } else {
            for (conf in newConfigs) {
                String fromConf = conf.key
                String toConf = conf.value
                project.logger.info(
                    "sourceDependencies: adding project dependency " +
                    "from ${project.group}:${project.name} conf=${fromConf} " +
                    "on :${targetName} conf=${toConf}"
                )
                project.dependencies.add(
                    fromConf,
                    rootProject.dependencies.project(path: ":${targetName}", configuration: toConf)
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

    public Task createFetchTask(Project project) {
        SourceDependency sourceDependency
        if (protocol == "svn") {
            def svnCommand = new CommandLine(project.logger, "svn.exe", project.&exec)
            sourceDependency = new SvnDependency(project, this, svnCommand)
        } else if (protocol == "hg") {
            def hgCommand = new CommandLine(project.logger, "hg.exe", project.&exec)
            sourceDependency = new HgDependency(project, this, hgCommand)
        } else if (protocol == "git") {
            def gitCommand = new CommandLine(project.logger, "git.exe", project.&exec)
            sourceDependency = new GitDependency(project, this, gitCommand)
        } else {
            throw new RuntimeException("Unsupported protocol: " + protocol)
        }
        String fetchTaskName = CamelCase.build("fetch", targetName)
        FetchSourceDependencyTask fetchTask =
            (FetchSourceDependencyTask)project.task(fetchTaskName, type: FetchSourceDependencyTask)
        fetchTask.description = sourceDependency.fetchTaskDescription
        fetchTask.initialize(sourceDependency)
        return fetchTask
    }

    /**
     * Returns the module version ID of the source dependency, unless there is no configuration mapping to it (in which
     * case returns {@code null}), or unless it has configuration mappings doesn't have a Gradle project which is
     * included in the build (in which case throws RuntimeException).
     * @return The module version ID of the source dependency.
     * @throws RuntimeException if the source dependency has configuration mappings but does not have a Gradle project.
     */
    public ModuleVersionIdentifier getDependencyId() {
        ModuleVersionIdentifier identifier = null
        if (configurationMappings.size() > 0) {
            Project dependencyProject = project.rootProject.findProject(targetName)
            if (dependencyProject == null) {
                // NOTE HughG: I've made this mistake a few times when modifying the settings.gradle code, so I put
                // in this explicit check and helpful message.
                throw new RuntimeException(
                    "Internal error: failed to find project '${targetName}'; " +
                    "maybe name override in settings.gradle was not done correctly."
                )
            }
            String groupName = dependencyProject.group.toString()
            String version = dependencyProject.version.toString()

            identifier = new DefaultModuleVersionIdentifier(groupName, targetName, version)
        }
        identifier
    }

    /**
     * Returns the module version ID of the source dependency as a string, unless there is no configuration mapping to
     * it (in which case returns {@code null}), or unless it has configuration mappings doesn't have a Gradle project
     * which is included in the build (in which case throws RuntimeException).
     * @return The module version ID string of the source dependency.
     * @throws RuntimeException if the source dependency has configuration mappings but does not have a Gradle project.
     */
    public String getDependencyCoordinate() {
        return dependencyId?.toString()
    }

    public Project getSourceDependencyProject() {
        project.rootProject.findProject(targetName)
    }

    void validate() {
        Project dependencyProject = project.rootProject.findProject(targetName)
        if (configurationMappings.size() == 0) {
            if (dependencyProject == null) {
                project.logger.debug "${project} has a source dependency on ${name}, which is not a Gradle project " +
                     "in this build -- it is a non-Gradle source checkout"
            } else {
                project.logger.warn "WARNING: ${project} has a source dependency on ${name}, which is a project in " +
                    "this build, but there is no configuration mapping.  This is probably a mistake.  It means that, " +
                    "when ${project.name} is published, there will be no real dependency on ${targetName}."
            }
        }
    }
}

package holygradle.source_dependencies

import holygradle.Helper
import holygradle.IntrepidPlugin
import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.util.CamelCase
import holygradle.dependencies.DependencyHandler
import holygradle.scm.CommandLine
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
        destinationDir = new File(project.projectDir, depName).getCanonicalFile()
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

    /**
     * @deprecated Methods of SourceDependencyPublishingHandler have moved out to SourceDependencyHandler or been
     * deleted, because the configuration mapping to source dependencies has an effect even if you are not publishing.
     */
    public SourceDependencyHandler getPublishing() {
        project.logger.warn(
            "The 'publishing' part of the syntax 'sourceDependencies { \"something\" { publishing ... } }' " +
            "is deprecated and will be removed.  Instead, its contents should be moved up one level, so " +
            "'sourceDependencies { \"something\" { publishing.configuration \"build->default\" ... } }' should be " +
            "'sourceDependencies { \"something\" { configuration \"build->default\" ... } }'."
        )
        return this
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
        if (!configurationMappings.contains(EVERYTHING_CONFIGURATION_MAPPING)) {
            configuration(IntrepidPlugin.EVERYTHING_CONFIGURATION_NAME)
        }
    }

    public Task createFetchTask(Project project, BuildScriptDependencies buildScriptDependencies) {
        SourceDependency sourceDependency
        if (protocol == "svn") {
            def hgCommand = new CommandLine(
                "svn.exe",
                project.&exec
            )
            sourceDependency = new SvnDependency(
                project,
                this,
                hgCommand
            )
        } else {
            def hgCommand = new CommandLine(
                "hg.exe",
                project.&exec
            )
            sourceDependency = new HgDependency(
                project,
                this,
                buildScriptDependencies,
                hgCommand
            )
        }
        String fetchTaskName = CamelCase.build("fetch", targetName)
        FetchSourceDependencyTask fetchTask =
            (FetchSourceDependencyTask)project.task(fetchTaskName, type: FetchSourceDependencyTask)
        fetchTask.description = sourceDependency.fetchTaskDescription
        fetchTask.initialize(sourceDependency)
        return fetchTask
    }

    /**
     * Returns the module version ID of the source dependency, unless it doesn't have a Gradle project, in which case
     * throws RuntimeException.
     * @return The module version ID of the source dependency.
     * @throws RuntimeException if the source dependency does not have a Gradle project.
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

    public String getDependencyCoordinate() {
        return dependencyId.toString()
    }

    public Project getSourceDependencyProject() {
        project.rootProject.findProject(targetName)
    }
}

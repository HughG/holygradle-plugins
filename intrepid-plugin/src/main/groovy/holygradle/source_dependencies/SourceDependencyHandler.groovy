package holygradle.source_dependencies

import holygradle.Helper
import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.util.CamelCase
import holygradle.dependencies.DependencyHandler
import holygradle.scm.CommandLine
import holygradle.scm.HgDependency
import holygradle.scm.SvnDependency
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

class SourceDependencyPublishingHandler {
    private final String dependencyName
    private final Project fromProject
    private Collection<String> originalConfigurations = []
    private Collection<AbstractMap.SimpleEntry<String, String>> configurations = []
    private String publishVersion = null
    private String publishGroup = null
    
    public SourceDependencyPublishingHandler(String dependencyName, Project fromProject) {
        this.dependencyName = dependencyName
        this.fromProject = fromProject
        configuration("everything")
    }
    
    public void configuration(String config) {
        originalConfigurations.add(config)
        Collection<AbstractMap.SimpleEntry<String, String>> newConfigs = []
        Helper.parseConfigurationMapping(config, newConfigs, "Formatting error for '$dependencyName' in 'sourceDependencies'.")
        configurations.addAll(newConfigs)
        
        // Add project dependencies between "sourceDependency"s, but only if they exist (e.g. after fAD).  This allows
        // Gradle's dependency resolution system to spot any conflicts between any _other_ dependencies in the
        // "fromProject" and the project for "dependencyName", such as packed dependencies.  See GR #2037.
        //
        // TODO 2013-05-15 HughG: I think ...
        //    - This belongs in SourceDependencyHandler, not in a "publishing" sub-object, because dependencies apply
        //      at build time, not only at publishing time.  The lack of build-time dependencies caused GR #2037.
        //    - This object shouldn't let you set the version, because at build time you just depend on the source which
        //      is there (which has no version) and at publishing time, you depend on whatever version gets published.
        //      It makes no sense to use one version of the source, but depend on a hard-coded published version.
        //    - This object shouldn't allow the group to be configured, because it can be inferred from the source
        //      dependency's project.  I don't think it's ever used, anyway.
        //    - Therefore, in fact, this class needn't exist at all.
        //
        // ... but I don't want to refactor anything until I have unit tests which check the effect of this method (i.e.,
        // that the correct ivy.xml file is produced).
        Project rootProject = this.fromProject.rootProject
        Project depProject = rootProject.findProject(":${this.dependencyName}")
        if (depProject == null) {
            if (!newConfigs.empty) {
                this.fromProject.logger.info(
                    "Not creating project dependencies from ${fromProject.name} on ${this.dependencyName}, " +
                        "because ${this.dependencyName} has no project file."
                )
            }
        } else if (!depProject.projectDir.exists()) {
            if (!newConfigs.empty) {
                this.fromProject.logger.info(
                    "Not creating project dependencies from ${fromProject.name} on ${this.dependencyName}, " +
                    "because ${this.dependencyName} has yet to be fetched."
                )
            }
        } else {
            for (conf in newConfigs) {
                String fromConf = conf.key
                String toConf = conf.value
                this.fromProject.logger.info "sourceDependencies: adding project dependency " +
                    "from ${fromProject.group}:${fromProject.name} conf=${fromConf} " +
                    "on :${this.dependencyName} conf=${toConf}"
                this.fromProject.dependencies.add(
                    fromConf,
                    rootProject.dependencies.project(path: ":${this.dependencyName}", configuration: toConf)
                )
            }
        }
    }
    
    public void configuration(String... configs) {
        configs.each { String config ->
            configuration(config)
        }
    }
    
    public void version(String ver) {
        publishVersion = ver
    }

    public void group(String g) {
        publishGroup = g
    }

    public String getPublishVersion() {
        publishVersion
    }

    public String getPublishGroup() {
        publishGroup
    }

    public Collection<AbstractMap.SimpleEntry<String, String>> getConfigurations() {
        return configurations
    }
}

class SourceDependencyHandler extends DependencyHandler {
    public String protocol = null
    public String url = null
    public String branch = null
    public Boolean writeVersionInfoFile = null
    public boolean export = false
    public SourceDependencyPublishingHandler publishingHandler
    public boolean usePublishedVersion = false
    private File destinationDir

    public static Collection<SourceDependencyHandler> createContainer(Project project) {
        project.extensions.sourceDependencies = project.container(SourceDependencyHandler) { String sourceDepName ->
            // Explicitly create the SourceDependencyHandler so we can add SourceDependencyPublishingHandler.

            SourceDependencyHandler sourceDep = new SourceDependencyHandler(sourceDepName, project)
            SourceDependencyPublishingHandler sourceDepPublishing = new SourceDependencyPublishingHandler(
                sourceDep.targetName,
                project
            )
            sourceDep.initialize(sourceDepPublishing)
            sourceDep  
        }
        project.sourceDependencies as Collection<SourceDependencyHandler>
    }

    public SourceDependencyHandler(String depName) {
        super(depName)
    }
    
    public SourceDependencyHandler(String depName, Project project) {
        super(depName, project)
        destinationDir = new File(project.projectDir, depName).getCanonicalFile()
    }
    
    public void initialize(SourceDependencyPublishingHandler publishing) {
        publishingHandler = publishing
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

    public SourceDependencyPublishingHandler getPublishing() {
        return publishingHandler
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
    
    public ModuleVersionIdentifier getLatestPublishedModule(Project project) {
        ModuleVersionIdentifier identifier = null
        if (publishingHandler.configurations.size() > 0) {
            String groupName = project.group.toString()
            String targetName = targetName
            String version = publishingHandler.publishVersion
            String firstTargetConfig = publishingHandler.configurations.find().value
            
            if (!usePublishedVersion) {                
                Project dependencyProject = project.rootProject.findProject(targetName)
                groupName = dependencyProject.group.toString()
                if (version == null) {
                    version = dependencyProject.version
                }
            }
            
            if (version == null) {
                version = project.version
            }
            if (version == "latest" || version == "latest.integration") {
                version = "+"
            }
            if (version.endsWith("+")) {
                Dependency externalDependency = new DefaultExternalModuleDependency(groupName, targetName, version, firstTargetConfig)
                Configuration dependencyConf = project.configurations.detachedConfiguration(externalDependency)
                dependencyConf.resolvedConfiguration.firstLevelModuleDependencies.each {
                    version = it.moduleVersion
                }
            }
            identifier = new DefaultModuleVersionIdentifier(groupName, targetName, version)
        }
        identifier
    }
    
    public String getDynamicPublishedDependencyCoordinate(Project project) {
        String group = publishingHandler.publishGroup
        if (group == null) {
            group = project.group
        }
        "${group}:${name}:${publishingHandler.publishVersion}"
    }
    
    public String getLatestPublishedDependencyCoordinate(Project project) {
        ModuleVersionIdentifier latest = getLatestPublishedModule(project)
        if (latest == null) {
            "${project.group}:${name} ??"
        } else {
            "${latest.getGroup()}:${latest.getName()}:${latest.getVersion()}"
        }
    }

    // NOTE 2013-11-07 HughG: I don't see why we need to pass in a project here.  The constructor for this class which
    // doesn't take project doesn't seem to be called anywhere, so we should just be able to use [this.project].  But
    // I think it's too risky to change without more investigation; e.g., it might break pinned source meta-packages,
    // so I'll leave it for now.
    public Project getSourceDependencyProject(Project project) {
        project.rootProject.findProject(targetName)
    }
}

package holygradle.source_dependencies

import holygradle.Helper
import holygradle.buildscript.BuildScriptDependencies
import holygradle.dependencies.DependencyHandler
import holygradle.scm.HgDependency
import holygradle.scm.SvnDependency
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import holygradle.custom_gradle.util.CamelCase

class SourceDependencyPublishingHandler {
    private final String dependencyName
    private final Project fromProject
    public def originalConfigurations = []
    public def configurations = []
    public String publishVersion = null
    public String publishGroup = null
    
    public SourceDependencyPublishingHandler(String dependencyName, Project fromProject) {
        this.dependencyName = dependencyName
        this.fromProject = fromProject
        configuration("everything")
    }
    
    public void configuration(String config) {
        originalConfigurations.add(config)
        Collection<AbstractMap.SimpleEntry<String, String>> newConfig = []
        Helper.parseConfigurationMapping(config, newConfig, "Formatting error for '$dependencyName' in 'sourceDependencies'.")
        configurations.addAll(newConfig)
        
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
        if ((depProject != null) && depProject.projectDir.exists()) {
            for (conf in newConfig) {
                String fromConf = conf.key
                String toConf = conf.value
                this.fromProject.dependencies.add(
                    fromConf,
                    rootProject.dependencies.project(path: ":${this.dependencyName}", configuration: toConf)
                )
            }
        } else {
            if (!newConfig.empty) {
                this.fromProject.logger.warn(
                    "Ignoring any project dependencies from ${fromProject.name} on ${this.dependencyName}, " +
                    "because ${this.dependencyName} has not yet been fetched."
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
}

class SourceDependencyHandler extends DependencyHandler {
    public String protocol = null
    public String url = null
    public String branch = null
    public def writeVersionInfoFile = null
    public def export = false
    public SourceDependencyPublishingHandler publishingHandler
    public boolean usePublishedVersion = false
    private File destinationDir
    private def overrideWarningMessages = []
    
    public static Collection<SourceDependencyHandler> createContainer(Project project) {
        project.extensions.sourceDependencies = project.container(SourceDependencyHandler) { sourceDepName ->
            // Explicitly create the SourceDependencyHandler so we can add SourceDependencyPublishingHandler.
            def sourceDep = project.sourceDependencies.extensions.create(sourceDepName, SourceDependencyHandler, sourceDepName, project)  
            def sourceDepPublishing = project.sourceDependencies."$sourceDepName".extensions.create("publishing", SourceDependencyPublishingHandler, sourceDep.getTargetName(), project)
            // Create a corresponding buildDependencies for the project, so custom-gradle-core can
            // offer useful functionality for it, to build scripts and other plugins.
            def buildDependencies = project.extensions.findByName("buildDependencies")
            if (buildDependencies != null) {
                buildDependencies.create(sourceDep.getTargetName())
            }
            sourceDep.initialize(sourceDepPublishing)
            sourceDep  
        }
        project.extensions.sourceDependencies
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
        if (url != null) {
            if (hgUrl != url) {
                overrideWarningMessages.add("'hg' - '${hgUrl}'")
            }
        } else {
            protocol = "hg"
            url = hgUrl
        }
    }
    
    public void svn(String svnUrl) {
        if (url != null) {
            if (svnUrl != url) {
                overrideWarningMessages.add("'svn' - '${svnUrl}'")
            }
        } else {
            protocol = "svn"
            url = svnUrl
        }
    }
    
    public Task createFetchTask(Project project, BuildScriptDependencies buildScriptDependencies) {
        def sourceDependency = null
        if (protocol == "svn") {
            sourceDependency = new SvnDependency(project, this)
        } else {
            sourceDependency = new HgDependency(project, this, buildScriptDependencies)
        }
        def fetchTaskName = CamelCase.build("fetch", getTargetName())
        def fetchTask = project.task(fetchTaskName, type: FetchSourceDependencyTask)
        if (overrideWarningMessages.size() > 0) {
            def messages = []
            messages.add "-"*80
            messages.add "Warning: source dependency '${name}' was configured with multiple urls."
            messages.add  "The first url (which is what will be used) is: '${protocol}' - '${url}'."
            messages.add  "The other urls (which are ignored) are: "
            overrideWarningMessages.each {
                messages.add "  ${it}"
            }
            messages.add "-"*80
            fetchTask.doFirst { 
                messages.each { println it }
            }
        }
        
        fetchTask.initialize(sourceDependency)
        return fetchTask
    }
    
    public ModuleVersionIdentifier getLatestPublishedModule(Project project) {
        ModuleVersionIdentifier identifier = null
        if (publishingHandler.configurations.size() > 0) {
            def group = project.group
            def targetName = getTargetName()
            def firstConfig = publishingHandler.configurations[0][1]
            if (!usePublishedVersion) {
                def dependencyProject = project.rootProject.findProject(targetName)
                group = dependencyProject.group
            }
            def version = publishingHandler.publishVersion
            if (version == null) {
                version = project.version
            }
            if (version == "latest" || version == "latest.integration") {
                version = "+"
            }
            if (version.endsWith("+")) {
                def externalDependency = new DefaultExternalModuleDependency(group, targetName, version, firstConfig)
                def dependencyConf = project.configurations.detachedConfiguration(externalDependency)                
                dependencyConf.getResolvedConfiguration().getFirstLevelModuleDependencies().each {
                    version = it.getModuleVersion()
                }
            }
            identifier = new DefaultModuleVersionIdentifier(group, targetName, version)
        }
        identifier
    }
    
    public def getDependenciesForPublishing(Project project) {
        def newDependencies = []
        if (publishingHandler.configurations.size() > 0) {
            def latestPublishedModule = getLatestPublishedModule(project)
           
            println "Published version for '${name}' is: ${latestPublishedModule.getVersion()}."
            
            publishingHandler.configurations.each { fromConfig, toConfig ->
                def depAttrMap = [:]
                depAttrMap["org"] = latestPublishedModule.getGroup()
                depAttrMap["name"] = latestPublishedModule.getName()
                depAttrMap["rev"] = latestPublishedModule.getVersion()
                if (fromConfig == toConfig) {
                    depAttrMap["conf"] = fromConfig
                } else {
                    depAttrMap["conf"] = "${fromConfig}->${toConfig}"
                }
                
                def relativePath = getFullTargetPath()
                if (relativePath != "/" && relativePath != "\\") {
                    depAttrMap["relativePath"] = relativePath
                }
                
                newDependencies.add(depAttrMap)
            }
        }
        newDependencies
    }
    
    public String getDynamicPublishedDependencyCoordinate(Project project) {
        def group = publishingHandler.publishGroup
        if (group == null) {
            group = project.group
        }
        "${group}:${name}:${publishingHandler.publishVersion}"
    }
    
    public String getLatestPublishedDependencyCoordinate(Project project) {
        def latest = getLatestPublishedModule(project)
        if (latest == null) {
            "${project.group}:${name} ??"
        } else {
            "${latest.getGroup()}:${latest.getName()}:${latest.getVersion()}"
        }
    }
    
    public Project getSourceDependencyProject(Project project) {
        project.findProject(getTargetName())
    }
}

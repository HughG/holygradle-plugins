package holygradle.publishing

import holygradle.dependencies.PackedDependencyHandler
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.unpacking.UnpackModule
import org.gradle.api.*
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.artifacts.repositories.*
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyModuleDescriptor
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.util.ConfigureUtil

public class DefaultPublishPackagesExtension implements PublishPackagesExtension {
    private final Project project
    private final PublishingExtension publishingExtension
    private final RepositoryHandler repositories
    private RepublishHandler republishHandler
    private String nextVersionNumberStr = null
    private String autoIncrementFilePath = null
    private String environmentVariableName = null
    public IvyModuleDescriptor mainIvyDescriptor
    private String publishGroup = null
    private String publishName = null
    
    public DefaultPublishPackagesExtension(
        Project project,
        PublishingExtension publishingExtension,
        Collection<SourceDependencyHandler> sourceDependencies,
        Collection<PackedDependencyHandler> packedDependencies
    ) {
        this.project = project
        this.publishingExtension = publishingExtension
        this.repositories = publishingExtension.getRepositories()
        final IvyPublication mainIvyPublication = (IvyPublication)publishingExtension.getPublications().getByName("ivy")
        mainIvyDescriptor = mainIvyPublication.getDescriptor()
                
        // Configure the publish task to deal with the version number, include source dependencies and convert 
        // dynamic dependency versions to fixed version numbers.
        project.gradle.projectsEvaluated {
            this.applyGroupName()
            this.applyModuleName()
            this.applyVersionNumber()
            
            Task ivyPublishTask = project.tasks.findByName("publishIvyPublicationToIvyRepository")
            if (ivyPublishTask != null) {
                
                // Make sure we start with no dependencies in the ivy.xml, because we manually generate
                // all the dependency information in the methods below (and overwrite the project
                // dependencies set-up by Gradle to include additional "relativePath" attribute)
                this.mainIvyDescriptor.withXml { xml ->
                    xml.asNode().dependencies.dependency.each { depNode ->
                        depNode.parent().remove(depNode)
                    }
                }
                                              
                this.includeSourceDependencies(project, sourceDependencies)
                this.removeUnwantedDependencies(packedDependencies)
                this.freezeDynamicDependencyVersions(project)
                this.fixUpConflictConfigurations()
                this.removePrivateConfigurations()
                this.addDependencyRelativePaths(packedDependencies)
                
                ivyPublishTask.doFirst {
                    this.verifyGroupName()
                    this.verifyVersionNumber()
                }
                ivyPublishTask.doLast {
                    this.incrementVersionNumber()
                }
            }
            
            // Override the group and description for the ivy-publish plugin's 'publish' task.
            Task publishTask = project.tasks.findByName("publish")
            if (publishTask != null) {
                publishTask.group = "Publishing"
                publishTask.description = "Publishes all publications for this module."
            }
            
            // Define a 'republish' task.
            if (getRepublishHandler() != null) {
                project.task("republish", type: DefaultTask) { Task task ->
                    task.group = "Publishing"
                    task.description = "'Republishes' the artifacts for the module."
                    if (ivyPublishTask != null) {
                        task.dependsOn ivyPublishTask
                    }
                }
            }
        }
    }
    
    public void defineCheckTask(Iterable<UnpackModule> unpackModules) {
        RepublishHandler republishHandler = getRepublishHandler()
        if (republishHandler != null) {
            String repoUrl = republishHandler.getToRepository()
            Collection<ArtifactRepository> repos = project.getRepositories().matching { repo ->
                repo instanceof AuthenticationSupported && repo.getCredentials().getUsername() != null
            }
            if (repos.size() > 0 && repoUrl != null) {
                AuthenticationSupported repo = (AuthenticationSupported)repos[0]
                project.task(
                    "checkPublishedDependencies",
                    type: CheckPublishedDependenciesTask
                ) { CheckPublishedDependenciesTask it ->
                    it.group = "Publishing"
                    it.description = "Check if all dependencies are accessible in the target repo."
                    it.initialize(unpackModules, repoUrl, repo.getCredentials())
                }
            }
        }
    }
    
    public void nextVersionNumber(String versionNo) {
        nextVersionNumberStr = versionNo
        applyVersionNumber()
    }
    
    public void nextVersionNumber(Closure versionNumClosure) {
        nextVersionNumber(versionNumClosure() as String)
    }
    
    public void nextVersionNumberAutoIncrementFile(String versionNumberFilePath) {
        autoIncrementFilePath = versionNumberFilePath
        applyVersionNumber()
    }
    
    public void nextVersionNumberEnvironmentVariable(String versionNumberEnvVar) {
        environmentVariableName = versionNumberEnvVar
        applyVersionNumber()
    }
    
    public RepositoryHandler getRepositories() {
        return repositories
    }
    
    public void repositories(Action<RepositoryHandler> configure) {
        configure.execute(repositories)
    }
    
    public void republish(Closure closure) {
        if (republishHandler == null) {
            republishHandler = new RepublishHandler()
        }
        ConfigureUtil.configure(closure, republishHandler)
    }
    
    public RepublishHandler getRepublishHandler() {
        if (project != project.rootProject && republishHandler == null) {
            return project.rootProject.publishPackages.getRepublishHandler()
        }
        return republishHandler
    }
    
    public String getPublishGroup() {
        publishGroup
    }
    
    public void group(String publishGroup) {
        this.publishGroup = publishGroup
        applyGroupName()
    }
    
    public String getPublishName() {
        publishName
    }
    
    public void name(String publishName) {
        this.publishName = publishName
    }
    
    private File getVersionFile() {
        return new File(project.getProjectDir(), autoIncrementFilePath)
    }
    
    private static String getVersionFromFile(File file)  {
        return file.text
    }
    
    private String getVersionFromFile()  {
        return getVersionFromFile(getVersionFile());
    }
    
    private static String incrementVersionNumber(File versionFile) {
        String versionStr = versionFile.text
        int lastDot = versionStr.lastIndexOf('.')
        int nextVersion = versionStr[lastDot+1..-1].toInteger() + 1
        String firstChunk = versionStr[0..lastDot]
        String newVersionStr = firstChunk + nextVersion.toString()
        versionFile.write(newVersionStr)
        newVersionStr
    }
    
    public void applyGroupName() {
        if (publishGroup != null) {
            project.group = publishGroup
        }
    }
    
    public void applyModuleName() {
        if (publishName != null) {
            // We'll implement this once the Gradle issue is fixed, and we can upgrade.
            throw new RuntimeException("Cannot apply module name because of http://issues.gradle.org/browse/GRADLE-2412")
        }
    }
    
    public void verifyGroupName() {
        if (project.group == null) {
            throw new RuntimeException("Please specify the group name for the published artifacts. Under 'publishPackages' something like 'group \"blah\"'.")
        }
    }
    
    public String applyVersionNumber() {
        if (nextVersionNumberStr != null) {
            project.version = nextVersionNumberStr
        } else if (autoIncrementFilePath != null) {
            File versionFile = getVersionFile();
            if (versionFile.exists()) {
                project.version = getVersionFromFile()
            }
        } else if (environmentVariableName != null) {
            String nextVersionNumber = System.getenv(environmentVariableName)
            if (nextVersionNumber != null) {
                project.version = nextVersionNumber
            }
        }
        project.version
    }
    
    public void verifyVersionNumber() {
        if (autoIncrementFilePath != null) {
            if (!getVersionFile().exists()) {
                throw new RuntimeException(
                    "'publishPackages' specifies nextVersionNumberAutoIncrementFile " + 
                    "\"${autoIncrementFilePath}\", but that file does not " +
                    "exist in the project directory: " + project.getProjectDir().getAbsolutePath()
                )
            }
        } else if (environmentVariableName != null) {
            String nextVersionNumber = System.getenv(environmentVariableName)
            if (nextVersionNumber == null) {
                throw new RuntimeException(
                    "Environment variable '${environmentVariableName}' " +
                    "is not set. This is necessary for 'publishPackages'."
                )
            }
        }
        
        int nonNullCount = 0
        if (nextVersionNumberStr != null) {
            nonNullCount++;
        }
        if (autoIncrementFilePath != null) {
            nonNullCount++;
        }
        if (environmentVariableName != null) {
            nonNullCount++;
        }
        if (nonNullCount == 0) {
            if (project.getVersion().toString() == "unspecified") {
                throw new RuntimeException(
                    "One of 'nextVersionNumber', 'nextVersionNumberAutoIncrementFile' and 'nextVersionNumberEnvironmentVariable' must be " +
                    "set on 'publishPackages' unless you set the 'version' property on the project yourself."
                )
            }
        } else if (nonNullCount > 1) {
            throw new RuntimeException(
                "Only one of 'nextVersionNumber', 'nextVersionNumberAutoIncrementFile' and 'nextVersionNumberEnvironmentVariable' should " +
                "be set on 'publishPackages'."
            )
        }
    }
    
    public String incrementVersionNumber() {
        if (autoIncrementFilePath != null) {
            incrementVersionNumber(getVersionFile());
        }
        return getCurrentVersionNumber();
    }
    
    private String getCurrentVersionNumber()  {
        String currentVersionNumber = null
        if (nextVersionNumberStr != null) {
            currentVersionNumber = nextVersionNumberStr
        } else if (autoIncrementFilePath != null) {
            currentVersionNumber = getVersionFromFile();
        } else if (environmentVariableName != null) {
            currentVersionNumber = System.getenv(environmentVariableName)
        }
        return currentVersionNumber
    }

    // For every source dependency we have, add equivalent binary dependencies in the published module descriptor.
    // (No point trying to use static types here as we're using GPath, so the returned objects have magic properties.)
    public void includeSourceDependencies(Project project, Iterable<SourceDependencyHandler> sourceDependencies) {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            Collection<Map<String, String>> newDependencies = []
            sourceDependencies.each { SourceDependencyHandler sourceDep ->
                newDependencies.addAll(sourceDep.getDependenciesForPublishing(project))
            }
            if (newDependencies.size() > 0) {
                def dependenciesNodes = xml.asNode().dependencies
                Node dependenciesNode = null
                if (dependenciesNodes.size() == 0) {
                    dependenciesNode = xml.asNode().appendNode("dependencies")
                } else {
                    dependenciesNode = dependenciesNodes.get(0)
                }
                newDependencies.each { newDep ->
                    dependenciesNode.appendNode("dependency", newDep)
                }
            }
        }
    }

    // Remove dependencies which were explicitly specified as "publishDependency = false", or whose configuration
    // name starts with "private".
    public void removeUnwantedDependencies(Collection<PackedDependencyHandler> packedDependencies) {
        Collection<String> unwantedDependencies = []
        packedDependencies.each { packedDep ->
            if (!packedDep.shouldPublishDependency()) {
                unwantedDependencies.add(packedDep.getDependencyName())
            }
        }

        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().dependencies.dependency.each { depNode ->
                if (unwantedDependencies.contains(depNode.@name) ||
                    depNode.@conf.startsWith("private")
                ) {
                    depNode.parent().remove(depNode)
                }
            }
        }
    }

    // Remove whole configurations, whose names start with "private".
    public void removePrivateConfigurations() {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().configurations.conf.each { confNode ->
                if (confNode.@name.startsWith("private")) {
                    def parent = confNode.parent()
                    parent.remove(confNode)
                }
            }
        }
    }

    private static String getDependencyVersion(Project project, String group, String module) {
        String version = null
        project.configurations.each { conf ->
            conf.resolvedConfiguration.getResolvedArtifacts().each { artifact ->
                ModuleVersionIdentifier ver = artifact.getModuleVersion().getId()
                if (ver.getGroup() == group && ver.getName() == module) {
                    version = ver.getVersion()
                }
            }
        }
        return version
    }

    // Replace the version of any dependencies which were specified with dynamic version numbers, so they have fixed
    // version numbers as resolved for the build which is to be published.
    public void freezeDynamicDependencyVersions(Project project) {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().dependencies.dependency.each { depNode ->
                if (depNode.@rev.endsWith("+")) {
                    depNode.@rev = getDependencyVersion(project, depNode.@org as String, depNode.@name as String)
                }
            }
        }
    }

    // See cookbook example: https://bitbucket.org/nm2501/holy-gradle-plugins/wiki/HolyGradleCookbook#!i-need-to-use-multiple-versions-of-the-same-component
    public void fixUpConflictConfigurations() {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().dependencies.dependency.each { depNode ->
                String conf = depNode.@conf as String
                String[] confSplit = conf.split("->")
                if (confSplit.size() == 1) {
                    confSplit = conf.split("-&gt;")
                }
                Collection<String> newConf = []
                confSplit.each { c ->
                    newConf.add(c.replaceAll("(.*)_conflict.*", { it[1] }))
                }
                depNode.@conf = newConf.join("->")
            }
        }
    }

    // This adds a custom "relativePath" attribute, to say where packedDependencies should be unpacked (or symlinked) to.
    public void addDependencyRelativePaths(Collection<PackedDependencyHandler> packedDependencies) {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().dependencies.dependency.each { depNode ->
                PackedDependencyHandler packedDep = packedDependencies.find {
                    it.getGroupName() == depNode.@org && 
                    it.getDependencyName() == depNode.@name &&
                    it.getVersionStr() == depNode.@rev
                }
                
                if (packedDep != null) {
                    depNode.@relativePath = packedDep.getFullTargetPath()
                }
            }
        }
    }
}
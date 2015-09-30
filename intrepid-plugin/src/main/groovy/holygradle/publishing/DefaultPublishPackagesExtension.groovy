package holygradle.publishing

import holygradle.dependencies.PackedDependencyHandler
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.unpacking.PackedDependenciesStateSource
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
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
    private final LinkedHashSet<String> originalConfigurationOrder = new LinkedHashSet()
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

        // Keep track of the order in which configurations are added, so that we can put them in the ivy.xml file in
        // the same order, for human-readability.
        final ConfigurationContainer configurations = project.configurations
        final LinkedHashSet<String> localOriginalConfigurationOrder = originalConfigurationOrder // capture private for closure
        configurations.whenObjectAdded((Closure){ Configuration c ->
            localOriginalConfigurationOrder.add(c.name)
        })
        configurations.whenObjectRemoved((Closure){ Configuration c ->
            localOriginalConfigurationOrder.remove(c.name)
        })

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

                this.freezeDynamicDependencyVersions(project)
                this.putConfigurationsInOriginalOrder()
                this.collapseMultipleConfigurationDependencies()
                this.addDependencyRelativePaths(project, packedDependencies, sourceDependencies)

                ivyPublishTask.doFirst {
                    this.failIfPackedDependenciesNotCreatingSymlink(packedDependencies)
                    this.verifyGroupName()
                    this.verifyVersionNumber()
                }
                ivyPublishTask.doLast {
                    this.incrementVersionNumber()
                }
            }

            Task generateDescriptorTask = project.tasks.findByName("generateIvyModuleDescriptor")
            if (generateDescriptorTask != null) {
                // Rewrite "&gt;" to ">" in generated ivy.xml files, for human-readability.
                generateDescriptorTask.doLast { Task t ->
                    if (t.didWork) {
                        t.outputs.files.files.each { File ivyFile ->
                            String contents = ivyFile.text
                            contents = contents.replaceAll("&gt;", ">")
                            ivyFile.text = contents
                        }
                    }
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
    
    public void defineCheckTask(PackedDependenciesStateSource packedDependenciesStateSource) {
        RepublishHandler republishHandler = getRepublishHandler()
        if (republishHandler != null) {
            String repoUrl = republishHandler.getToRepository()
            Collection<ArtifactRepository> repos = project.getRepositories().matching { repo ->
                repo instanceof AuthenticationSupported && repo.getCredentials().getUsername() != null
            }
            if (repos.size() > 0 && repoUrl != null) {
                AuthenticationSupported repo = (AuthenticationSupported)repos[0]
                project.task(
                    "checkPackedDependencies",
                    type: CheckPublishedDependenciesTask
                ) { CheckPublishedDependenciesTask it ->
                    it.group = "Publishing"
                    it.description = "Check if all packed dependencies are accessible in the target repo."
                    it.initialize(packedDependenciesStateSource, repoUrl, repo.getCredentials())
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
    
    public void group(String publishGroup) {
        this.publishGroup = publishGroup
        applyGroupName()
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
    
    private static String doIncrementVersionNumber(File versionFile) {
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
            doIncrementVersionNumber(getVersionFile());
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

    // Throw an exception if any packed dependencies are marked with noCreateSymlinkToCache()
    public void failIfPackedDependenciesNotCreatingSymlink(Collection<PackedDependencyHandler> packedDependencies) {
        Collection<PackedDependencyHandler> nonSymlinkedPackedDependencies =
            packedDependencies.findAll { it.shouldUnpackToCache() && !it.shouldCreateSymlinkToCache() }
        if (!nonSymlinkedPackedDependencies.empty) {
            String dependenciesDescription = (nonSymlinkedPackedDependencies*.dependencyCoordinate).join(", ")
            throw new RuntimeException(
                "Cannot publish ${project.name} because some packed dependencies are using noCreateSymlinkToCache(), " +
                "which means Gradle cannot know the real version of those dependencies: [${dependenciesDescription}]"
            )
        }
    }

    // Re-writes the "configurations" element so that its children appear in the same order that the configurations were
    // defined in the project.
    public void putConfigurationsInOriginalOrder() {
        final LinkedHashSet<String> localOriginalConfigurationOrder = originalConfigurationOrder // capture private for closure
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().configurations.each { Node confsNode ->
                LinkedHashMap<String, Node> confNodes = new LinkedHashMap()
                localOriginalConfigurationOrder.each { String confName ->
                    Node confNode = (Node)confsNode.find { it.attribute("name") == confName }
                    // Private configurations will have been removed, and so return null.
                    if (confNode != null) {
                        confsNode.remove(confNode)
                        confNodes[confName] = confNode
                    }
                }
                confNodes.values().each { Node confNode ->
                    confsNode.append(confNode)
                }
            }
        }
    }

    private static String getDependencyVersion(Project project, String group, String module) {
        String version = null
        project.configurations.each((Closure){ conf ->
            conf.resolvedConfiguration.getResolvedArtifacts().each { artifact ->
                ModuleVersionIdentifier ver = artifact.getModuleVersion().getId()
                if (ver.getGroup() == group && ver.getName() == module) {
                    version = ver.getVersion()
                }
            }
        })
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

    // This method goes through the "dependencies" node and converts each set of "dependency" children with the same
    // "org"/"name"/"rev" (but different "conf") attribute values into a single "dependency" node with a list-style
    // configuration mapping in the "conf" attribute ("a1->b1;a2->b2").  This is for human-readability.
    //
    // This method must be called before addDependencyRelativePaths, because it will drop attributes other than the ones
    // mentioned above.
    public void collapseMultipleConfigurationDependencies() {
        // WARNING: This method loses any sub-nodes of each dependency node.  These can be "conf" (not needed, as we use
        // the "@conf" attribute) and "artifact", "exclude", "include" (only needed to override the target's ivy file,
        // which I don't think we care about for now).

        // Pre-calculate the indices for the original conficguration order, so we can easily sort the configuration
        // mapping versions by source config, so that the order matches the order in which the configs were defined.
        final LinkedHashMap<String, Integer> configIndices = [:]
        originalConfigurationOrder.eachWithIndex { String entry, int i -> configIndices[entry] = i }

        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            Map<String, List<String>> configsByCoord = new LinkedHashMap().withDefault { new ArrayList() }
            xml.asNode().dependencies.each { depsNode ->
                depsNode.dependency.each { depNode ->
                    String coord = "${depNode.@org}:${depNode.@name}:${depNode.@rev}"
                    configsByCoord[coord] << ((String)depNode.@conf)
                }
                depsNode.children().clear()
                configsByCoord.each { String coord, List<String> configs ->
                    List<String> c = coord.split(":")
                    List<String> sortedConfigs = configs.sort { a, b ->
                        configIndices[a.split("->")[0]] <=> configIndices[b.split("->")[0]]
                    }
                    depsNode.appendNode("dependency", [
                        "org": c[0],
                        "name": c[1],
                        "rev": c[2],
                        "conf": sortedConfigs.join(";")
                    ])
                }
            }
        }
    }

    // This adds a custom "relativePath" attribute, to say where packedDependencies should be unpacked (or symlinked) to.
    public void addDependencyRelativePaths(Project project, Collection<PackedDependencyHandler> packedDependencies, Collection<SourceDependencyHandler> sourceDependencies) {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().'@xmlns:holygradle' = 'http://holy-gradle/'
            xml.asNode().dependencies.dependency.each { depNode ->
                // If the dependency is a packed dependency, get its relative path from the 
                // gradle script's packedDependencyHandler
                PackedDependencyHandler packedDep = packedDependencies.find {
                    it.getGroupName() == depNode.@org && 
                    it.getDependencyName() == depNode.@name &&
                    it.getVersionStr() == depNode.@rev
                }
                
                if (packedDep != null) {
                    project.logger.info "Adding relative path to packedDep node: ${packedDep.getGroupName()}:${packedDep.getDependencyName()}:${packedDep.getVersionStr()} path=${packedDep.getFullTargetPath()}"
                    depNode.@relativePath = packedDep.getFullTargetPath()
                } else {
                    // Else if the dependency is a source dependency, get its relative path from the 
                    // gradle script's sourceDependencyHandler
                    SourceDependencyHandler sourceDep = sourceDependencies.find {
                        ModuleVersionIdentifier latestPublishedModule = it.getLatestPublishedModule(project)
                        latestPublishedModule.getGroup() == depNode.@org && 
                        latestPublishedModule.getName() == depNode.@name &&
                        latestPublishedModule.getVersion() == depNode.@rev
                    }
                    
                    if (sourceDep != null) {
                        project.logger.info "Adding relative path to sourceDep node: ${depNode.@org}:${depNode.@name}:${depNode.@rev} path=${sourceDep.getFullTargetPath()}"
                        depNode.@relativePath = sourceDep.getFullTargetPath()
                        depNode.'@holygradle:absolutePath' = sourceDep.getAbsolutePath().toString()
                        depNode.'@holygradle:isSource' = true
                    } else {
                        project.logger.warn "Did not find dependency ${depNode.@org}:${depNode.@name}:${depNode.@rev} in source or packed dependencies"
                    }
                }
            }
        }
    }
}
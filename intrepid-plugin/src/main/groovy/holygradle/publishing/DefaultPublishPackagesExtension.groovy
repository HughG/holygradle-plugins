package holygradle.publishing

import holygradle.dependencies.PackedDependencyHandler
import holygradle.packaging.PackageArtifactHandler
import holygradle.unpacking.PackedDependenciesStateSource
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.invocation.Gradle
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.util.ConfigureUtil

public class DefaultPublishPackagesExtension implements PublishPackagesExtension {
    private final Project project
    private boolean createDefaultPublication = true
    private boolean publishPrivateConfigurations = true
    private final LinkedHashSet<String> originalConfigurationOrder = new LinkedHashSet()
    private RepublishHandler republishHandler

    public static final String DEFAULT_PUBLICATION_NAME = "ivy"
    public static final String DEFAULT_DESCRIPTOR_TASK_NAME =
        "generateDescriptorFileFor${DEFAULT_PUBLICATION_NAME.capitalize()}Publication"

    public DefaultPublishPackagesExtension(
        Project project,
        NamedDomainObjectContainer<PackageArtifactHandler> packageArtifactHandlers,
        Collection<PackedDependencyHandler> packedDependencies
    ) {
        this.project = project

        // Keep track of the order in which configurations are added, so that we can put them in the ivy.xml file in
        // the same order, for human-readability.
        final ConfigurationContainer configurations = project.configurations
        setupRecordingOfOriginalConfigurationOrder(configurations)

        Task beforeGenerateDescriptorTask = project.task("beforeGenerateDescriptor") { Task t ->
            t.group = "Publishing"
            t.description = "Actions to run before any Ivy descriptor generation tasks"
        }
        Task generateIvyModuleDescriptorTask = project.task("generateIvyModuleDescriptor") { Task t ->
            t.group = "Publishing"
            t.description = "Backwards-compatibility task for generating ivy.xml files for publication. " +
                "This task will be removed in a future version of the Holy Gradle. It has been replaced by " +
                "tasks with name generateDescriptorFileFor<NAME OF PUBLICATION>Publication."
            t.doFirst {
                t.logger.warn(
                    "WARNING: Task ${t.name} will be removed in a future version of the Holy Gradle. " +
                    "It has been replaced by tasks with name generateDescriptorFileFor<NAME OF PUBLICATION>Publication."
                )
            }
        }

        beforeGenerateDescriptorTask.doLast {
            this.failIfPackedDependenciesNotCreatingLink(packedDependencies)
        }

        // Define a 'republish' task.
        Task republishTask = null
        if (getRepublishHandler() != null) {
            republishTask = project.task("republish", type: DefaultTask) { Task task ->
                task.group = "Publishing"
                task.description = "'Republishes' the artifacts for the module."
            }
        }

        // Configure the publish task to deal with the version number, include source dependencies and convert
        // dynamic dependency versions to fixed version numbers.  We do this in a projectsEvaluated block so that
        // source dependency projects have been evaluated, so we can be sure their information is available.
        project.gradle.projectsEvaluated { Gradle gradle ->
            project.publishing { PublishingExtension it ->
                if (createDefaultPublication) {
                    createDefaultPublication(it, packageArtifactHandlers)
                    configureGenerateDescriptorTasks(
                        beforeGenerateDescriptorTask,
                        generateIvyModuleDescriptorTask,
                        project
                    )
                    configureRepublishTaskDependencies(republishTask)
                }
            }
        }
    }

    private void setupRecordingOfOriginalConfigurationOrder(ConfigurationContainer configurations) {
        final LinkedHashSet<String> localOriginalConfigurationOrder = originalConfigurationOrder
        // capture private for closure
        configurations.whenObjectAdded((Closure) { Configuration c ->
            localOriginalConfigurationOrder.add(c.name)
        })
        configurations.whenObjectRemoved((Closure) { Configuration c ->
            localOriginalConfigurationOrder.remove(c.name)
        })
    }

    // Public for use from closure.
    public createDefaultPublication(
        PublishingExtension publishing,
        NamedDomainObjectContainer<PackageArtifactHandler> packageArtifactHandlers
    ) {
        final Project localProject = project
        publishing.publications { pubs ->
            IvyPublication defaultPublication = pubs.maybeCreate(DEFAULT_PUBLICATION_NAME, IvyPublication)
            final Action<IvyPublication> configureAction = ConfigureUtil.configureUsing { pub ->
                // NOTE: We use .each instead of .all here because we have to fix the state of the publication within
                // this closure.  If some other callback adds more configurations or packaged artifacts on the fly
                // later, they won't be included in the publication.

                // Note that the default ivy-publish behaviour in Gradle 1.4 was to add only visible configurations and
                // the ones they extended from (even if those super-configurations were not themselves public).
                localProject.configurations.each((Closure) { Configuration conf ->
                    if (includeConfiguration(conf)) {
                        pub.configurations { ivyConfs ->
                            IvyConfiguration ivyConf = ivyConfs.maybeCreate(conf.name)
                            conf.extendsFrom.each { ivyConf.extend(it.name) }
                        }
                    }
                })

                // Add an artifact to this publication for each handler that has been or is later added.
                packageArtifactHandlers.each { PackageArtifactHandler handler ->
                    if (includeConfiguration(localProject.configurations.getByName(handler.configuration))) {
                        AbstractArchiveTask packageTask =
                                localProject.tasks.getByName(handler.packageTaskName) as AbstractArchiveTask
                        // Gradle automatically converts Closure to the right Action<> type.
                        //noinspection GroovyAssignabilityCheck
                        pub.artifact(packageTask) { IvyArtifact artifact ->
                            artifact.name = packageTask.baseName
                            artifact.conf = handler.configuration
                        }
                    }
                }
            }
            configureAction.execute(defaultPublication)
        }
    }

    // Public for use from closure.
    public configureGenerateDescriptorTasks(
        Task beforeGenerateDescriptorTask,
        Task generateIvyModuleDescriptorTask,
        Project project
    ) {
        project.tasks.withType(GenerateIvyDescriptor).whenTaskAdded { GenerateIvyDescriptor descriptorTask ->
            generateIvyModuleDescriptorTask.dependsOn descriptorTask
            descriptorTask.dependsOn beforeGenerateDescriptorTask

            descriptorTask.doFirst {
                // From Gradle 1.7, the default status is 'integration', but we prefer the previous behaviour.
                descriptorTask.descriptor.status = project.status
                putConfigurationsInOriginalOrder(descriptorTask.descriptor)
                freezeDynamicDependencyVersions(project, descriptorTask.descriptor)
                collapseMultipleConfigurationDependencies(descriptorTask.descriptor)
            }

            // We call this here because it uses doFirst, and we need this to happen after
            // putConfigurationsInOriginalOrder and collapseMultipleConfigurationDependencies.  We do the adding and
            // the re-ordering separately so that we can also do re-ordering for publications other than our default one.
            if (descriptorTask.name == DEFAULT_DESCRIPTOR_TASK_NAME) {
                descriptorTask.doFirst {
                    addConfigurationDependenciesToDefaultPublication(descriptorTask)
                }
            }

            descriptorTask.doLast {
                fixArrowsInXml(descriptorTask)
            }
        }
    }

    // Public for use from closure.
    public configureRepublishTaskDependencies(Task republishTask) {
        project.tasks.withType(PublishToIvyRepository).whenTaskAdded { PublishToIvyRepository publishTask ->
            if (republishTask != null) {
                republishTask.dependsOn publishTask
            }
        }
    }

    // This is public only so that it can be called from within a closure.
    public boolean includeConfiguration(Configuration conf) {
        return conf.visible ||
            this.publishPrivateConfigurations ||
            project.configurations.any((Closure){ it.extendsFrom conf })
    }

    public void defineCheckTask(PackedDependenciesStateSource packedDependenciesStateSource) {
        RepublishHandler republishHandler = getRepublishHandler()
        if (republishHandler != null) {
            String repoUrl = republishHandler.toRepository
            Collection<ArtifactRepository> repos = project.repositories.matching { repo ->
                repo instanceof AuthenticationSupported && repo.credentials.username != null
            }
            if (repos.size() > 0 && repoUrl != null) {
                AuthenticationSupported repo = (AuthenticationSupported)repos[0]
                project.task(
                    "checkPackedDependencies",
                    type: CheckPublishedDependenciesTask
                ) { CheckPublishedDependenciesTask it ->
                    it.group = "Publishing"
                    it.description = "Check if all packed dependencies are accessible in the target repo."
                    it.initialize(packedDependenciesStateSource, repoUrl, repo.credentials)
                }
            }
        }
    }

    @Override
    public RepositoryHandler getRepositories() {
        throw new UnsupportedOperationException(
            "Repositories for publishing can only be configured using a closure, not by accessing the repositories " +
            "collection as a property.  That means, if you have a build script from an older verson of the Holy " +
            "Gradle which has 'publishPackages { repositories.ivy { ... } }' you must change it to " +
            "'publishPackages { repositories { ivy { ... } } }'"
        )
    }

    @Override
    public void repositories(Action<RepositoryHandler> configure) {
        project.publishing {
            repositories { RepositoryHandler handler ->
                configure.execute(handler)
            }
        }
    }

    @Override
    boolean getCreateDefaultPublication() {
        this.createDefaultPublication
    }

    @Override
    void setCreateDefaultPublication(boolean create) {
        this.createDefaultPublication = create
    }

    @Override
    boolean getPublishPrivateConfigurations() {
        this.publishPrivateConfigurations
    }

    @Override
    void setPublishPrivateConfigurations(boolean publish) {
        this.publishPrivateConfigurations = publish
    }

    @Override
    void defaultPublication(Action<Publication> configure) {
        project.publishing { PublishingExtension publishing ->
            if (!createDefaultPublication) {
                throw new RuntimeException(
                    "Cannot configure the default publication because it will not be created, " +
                    "because createDefaultPublication is false."
                )
            }
            configure.execute(publishing.publications.maybeCreate(DEFAULT_PUBLICATION_NAME, IvyPublication))
        }
    }

    public void republish(Closure closure) {
        if (republishHandler == null) {
            republishHandler = new RepublishHandler()
        }
        ConfigureUtil.configure(closure, republishHandler)
    }

    public RepublishHandler getRepublishHandler() {
        if (project != project.rootProject && republishHandler == null) {
            return project.rootProject.publishPackages.republishHandler
        }
        return republishHandler
    }
    
    // Throw an exception if any packed dependencies are marked with noCreateLinkToCache()
    public void failIfPackedDependenciesNotCreatingLink(Collection<PackedDependencyHandler> packedDependencies) {
        Collection<PackedDependencyHandler> nonLinkedPackedDependencies =
            packedDependencies.findAll { it.shouldUnpackToCache() && !it.shouldCreateLinkToCache() }
        if (!nonLinkedPackedDependencies.empty) {
            String dependenciesDescription = (nonLinkedPackedDependencies*.dependencyCoordinate).join(", ")
            throw new RuntimeException(
                "Cannot publish ${project.name} because some packed dependencies are using noCreateLinkToCache(), " +
                "which means Gradle cannot know the real version of those dependencies: [${dependenciesDescription}]"
            )
        }
    }

    // Re-writes the "configurations" element so that its children appear in the same order that the configurations were
    // defined in the project.
    public void putConfigurationsInOriginalOrder(IvyModuleDescriptorSpec descriptor) {
        final LinkedHashSet<String> localOriginalConfigurationOrder = originalConfigurationOrder // capture private for closure
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        descriptor.withXml { xml ->
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

    // TODO 2016-07-03 HughG: This should use the source configurations from the dependency XML node
    // instead of searching all configurations.  Otherwise we may get the wrong answer if there's
    // more than one version of the same dependency, in different configurations.
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
    public static void freezeDynamicDependencyVersions(Project project, IvyModuleDescriptorSpec descriptor) {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        descriptor.withXml { xml ->
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
    public void collapseMultipleConfigurationDependencies(IvyModuleDescriptorSpec descriptor) {
        // WARNING: This method loses any sub-nodes of each dependency node.  These can be "conf" (not needed, as we use
        // the "@conf" attribute) and "artifact", "exclude", "include" (only needed to override the target's ivy file,
        // which I don't think we care about for now).

        // Pre-calculate the indices for the original conficguration order, so we can easily sort the configuration
        // mapping versions by source config, so that the order matches the order in which the configs were defined.
        final LinkedHashMap<String, Integer> configIndices = [:]
        originalConfigurationOrder.eachWithIndex { String entry, int i -> configIndices[entry] = i }

        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        descriptor.withXml { xml ->
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

    public void addConfigurationDependenciesToDefaultPublication(GenerateIvyDescriptor descriptorTask) {
        descriptorTask.descriptor.withXml { xml ->
            Node depsNode = xml.asNode().dependencies.find() as Node
            descriptorTask.project.configurations.each((Closure) { Configuration conf ->
                conf.dependencies.withType(ModuleDependency).each { ModuleDependency dep ->
                    depsNode.appendNode("dependency", [
                        "org" : dep.group,
                        "name": dep.name,
                        "rev" : dep.version,
                        "conf": "${conf.name}->${dep.targetConfiguration}"
                    ])
                }
            })
        }
    }

    // Add dependencies to this publication for each ModuleDependency of each Configuration of this Project.
    public void fixArrowsInXml(GenerateIvyDescriptor ivyDescriptorTask) {
        // Rewrite "&gt;" to ">" in generated ivy.xml files, for human-readability.
        if (ivyDescriptorTask.didWork) {
            ivyDescriptorTask.outputs.files.files.each { File ivyFile ->
                String contents = ivyFile.text
                contents = contents.replaceAll("&gt;", ">")
                ivyFile.text = contents
            }
        }
    }
}
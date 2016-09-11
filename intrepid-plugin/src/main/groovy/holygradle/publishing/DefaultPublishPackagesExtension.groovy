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
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.util.ConfigureUtil

public class DefaultPublishPackagesExtension implements PublishPackagesExtension {
    private final Project project
    private final PublishingExtension publishingExtension
    private final RepositoryHandler repositories
    private boolean createDefaultPublication = true
    private boolean publishPrivateConfigurations = true
    public final IvyPublication ivyPublication
    private final LinkedHashSet<String> originalConfigurationOrder = new LinkedHashSet()
    private RepublishHandler republishHandler

    public DefaultPublishPackagesExtension(
        Project project,
        NamedDomainObjectContainer<PackageArtifactHandler> packageArtifactHandlers,
        PublishingExtension publishingExtension,
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
        this.repositories = publishingExtension.repositories
        IvyPublication ivyPublication = null
        // Gradle automatically converts Closure to the right Action<> type.
        //noinspection GroovyAssignabilityCheck
        publishingExtension.publications { pubs ->
            ivyPublication = pubs.maybeCreate("ivy", IvyPublication)
        }
        this.ivyPublication = ivyPublication

        // Add a configuration to this publication for each one that has been or is later added to the project, and
        // remove them if and when any are removed.  Note that the default ivy-publish behaviour in Gradle 1.4 was to
        // add only visible configurations and the ones they extended from (even if those super-configurations were not
        // themselves public.
        configurations.all((Closure){ Configuration conf ->
            // Gradle automatically converts Closure to the right Action<> type.
            //noinspection GroovyAssignabilityCheck
            if (includeConfiguration(conf)) {
                ivyPublication.configurations { ivyConfs ->
                    IvyConfiguration ivyConf = ivyConfs.maybeCreate(conf.name)
                    conf.extendsFrom.each { ivyConf.extend(it.name) }
                }
            }
        })
        configurations.whenObjectRemoved((Closure){ Configuration conf ->
            ivyPublication.configurations.each { IvyConfiguration ivyConf ->
                ivyConf.extends.remove(conf.name)
            }
            IvyConfiguration ivyConf = ivyPublication.configurations.findByName(conf.name)
            ivyPublication.configurations.remove(ivyConf)
        })

        // Add an artifact to this publication for each handler that has been or is later added, and remove artifacts
        // if and when any are removed.
        packageArtifactHandlers.all { PackageArtifactHandler handler ->
            if (includeConfiguration(project.configurations.getByName(handler.configuration))) {
                AbstractArchiveTask packageTask = project.tasks.getByName(handler.packageTaskName) as AbstractArchiveTask
                // Gradle automatically converts Closure to the right Action<> type.
                //noinspection GroovyAssignabilityCheck
                ivyPublication.artifact(packageTask) { IvyArtifact artifact ->
                    artifact.name = packageTask.baseName
                    artifact.conf = handler.configuration
                }
            }
        }
        packageArtifactHandlers.whenObjectRemoved { PackageArtifactHandler handler ->
            AbstractArchiveTask packageTask = project.tasks.getByName(handler.packageTaskName) as AbstractArchiveTask
            ivyPublication.artifacts.remove(ivyPublication.artifacts.find { it.file == packageTask.archivePath })
        }

        Task beforeGenerateDescriptorTask = project.task("beforeGenerateDescriptor") { Task t ->
            t.group = "Publishing"
            t.description = "Actions to run before any Ivy descriptor generation tasks"
        }
        Task afterGenerateDescriptorTask = project.task("afterGenerateDescriptor") { Task t ->
            t.group = "Publishing"
            t.description = "Actions to run after all Ivy descriptor generation tasks"
        }
        Task beforePublishTask = project.task("beforePublish") { Task t ->
            t.group = "Publishing"
            t.description = "Actions to run before any publish tasks"
        }
        Task afterPublishTask = project.task("afterPublish") { Task t ->
            t.group = "Publishing"
            t.description = "Actions to run after all publish tasks"
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

        beforeGenerateDescriptorTask << {
            this.failIfPackedDependenciesNotCreatingLink(packedDependencies)
        }

        // Configure the publish task to deal with the version number, include source dependencies and convert
        // dynamic dependency versions to fixed version numbers.
        project.gradle.projectsEvaluated {
            TaskCollection<GenerateIvyDescriptor> ivyDescriptorTasks = project.tasks.withType(GenerateIvyDescriptor)
            TaskCollection<PublishToIvyRepository> ivyPublishTasks = project.tasks.withType(PublishToIvyRepository)

            ivyDescriptorTasks.each {
                generateIvyModuleDescriptorTask.dependsOn it

                it.dependsOn beforeGenerateDescriptorTask
                it.finalizedBy afterGenerateDescriptorTask
                // Add a mustRunAfter to make sure the the after task doesn't run until all publish tasks are done.
                afterGenerateDescriptorTask.mustRunAfter it
            }

            ivyPublishTasks.each {
                it.dependsOn beforePublishTask
                it.finalizedBy afterPublishTask
                // Add a mustRunAfter to make sure the afterPublish doesn't run until all publish tasks are done.
                afterPublishTask.mustRunAfter it

                Task generateDescriptorTask =
                    project.tasks.getByName("generateDescriptorFileFor${it.publication.name.capitalize()}Publication")
                generateDescriptorTask.ext['publication'] = it.publication
            }

            // Only use our default publication if the user asked for it.
            if (!createDefaultPublication) {
                publishingExtension.publications.remove(ivyPublication)
            }

            ivyDescriptorTasks.each { ivyDescriptorTask ->
                ivyDescriptorTask.doFirst {
                    IvyPublication publication = ivyDescriptorTask.ext['publication'] as IvyPublication
                    // From Gradle 1.7, the default status is 'integration', but we prefer the previous behaviour.
                    publication.descriptor.status = project.status
                    putConfigurationsInOriginalOrder(publication)
                    freezeDynamicDependencyVersions(project, publication)
                    collapseMultipleConfigurationDependencies(publication)
                }
                ivyDescriptorTask.doLast {
                    fixArrowsInXml(ivyDescriptorTask)
                }
            }

            // We do this after calling collapseMultipleConfigurationDependencies because they both use doFirst, and we
            // need the setup of configuration dependencies to happen before we re-order them.  We do the adding and the
            // re-ordering separately so that we can also do re-ordering for publications other than our default one.
            maybeAddConfigurationDependenciesToDefaultPublication()

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
                    ivyPublishTasks.each { task.dependsOn it }
                }
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
    
    public RepositoryHandler getRepositories() {
        return repositories
    }

    public void repositories(Action<RepositoryHandler> configure) {
        configure.execute(repositories)
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

    Publication getDefaultPublication() {
        return ivyPublication
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
    public void putConfigurationsInOriginalOrder(IvyPublication publication) {
        final LinkedHashSet<String> localOriginalConfigurationOrder = originalConfigurationOrder // capture private for closure
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        publication.descriptor.withXml { xml ->
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
    public static void freezeDynamicDependencyVersions(Project project, IvyPublication publication) {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        publication.descriptor.withXml { xml ->
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
    public void collapseMultipleConfigurationDependencies(IvyPublication publication) {
        // WARNING: This method loses any sub-nodes of each dependency node.  These can be "conf" (not needed, as we use
        // the "@conf" attribute) and "artifact", "exclude", "include" (only needed to override the target's ivy file,
        // which I don't think we care about for now).

        // Pre-calculate the indices for the original conficguration order, so we can easily sort the configuration
        // mapping versions by source config, so that the order matches the order in which the configs were defined.
        final LinkedHashMap<String, Integer> configIndices = [:]
        originalConfigurationOrder.eachWithIndex { String entry, int i -> configIndices[entry] = i }

        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        publication.descriptor.withXml { xml ->
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

    public void maybeAddConfigurationDependenciesToDefaultPublication() {
        // Our default ivy publication may have been removed, if others were added.
        if (this.createDefaultPublication) {
            String generateIvyDescriptorTaskName =
                "generateDescriptorFileFor${this.ivyPublication.name.capitalize()}Publication".toString()
            project.logger.debug "Descriptor tasks for ${project}: ${project.tasks.withType(GenerateIvyDescriptor)*.name}"
            project.logger.debug "All tasks for ${project}: ${project.tasks*.name}"
            final Project p = project
            project.gradle.taskGraph.whenReady {
                p.logger.debug "Descriptor tasks for ${p} whenReady: ${p.tasks.withType(GenerateIvyDescriptor)*.name}"
                p.logger.debug "All tasks for ${p} whenReady: ${p.tasks*.name}"
                Task generateIvyDescriptorTask = p.tasks.findByName(generateIvyDescriptorTaskName)
                // There may not be a "generate descriptor" task, if the project doesn't package anything.
                generateIvyDescriptorTask?.doFirst {
                    ivyPublication.descriptor.withXml { xml ->
                        Node depsNode = xml.asNode().dependencies.find() as Node
                        p.configurations.each((Closure) { Configuration conf ->
                            conf.dependencies.withType(ModuleDependency).each { ModuleDependency dep ->
                                depsNode.appendNode("dependency", [
                                    "org" : dep.group,
                                    "name": dep.name,
                                    "rev" : dep.version,
                                    "conf": "${conf.name}->${dep.configuration}"
                                ])
                            }
                        })
                    }
                }
            }
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
package holygradle.publishing

import holygradle.dependencies.PackedDependencyHandler
import holygradle.packaging.PackageArtifactHandler
import holygradle.unpacking.PackedDependenciesStateSource
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.script.lang.kotlin.task
import org.w3c.dom.Node

class DefaultPublishPackagesExtension(
        private val project: Project,
        packageArtifactHandlers: NamedDomainObjectContainer<PackageArtifactHandler>,
        packedDependencies: Collection<PackedDependencyHandler>
) : PublishPackagesExtension {
    companion object {
        const val DEFAULT_PUBLICATION_NAME = "ivy"
        val DEFAULT_DESCRIPTOR_TASK_NAME = "generateDescriptorFileFor${DEFAULT_PUBLICATION_NAME.capitalize()}Publication"
    }
    override var createDefaultPublication = true
    override var publishPrivateConfigurations = true
    override var republishHandler: RepublishHandler? = null
    private val originalConfigurationOrder = linkedSetOf<String>()

    init {
        // Keep track of the order in which configurations are added, so that we can put them in the ivy.xml file in
        // the same order, for human-readability.
        val configurations = project.configurations
        setupRecordingOfOriginalConfigurationOrder(configurations)

        val beforeGenerateDescriptorTask = project.task("beforeGenerateDescriptor") { Task t ->
            t.group = "Publishing"
            t.description = "Actions to run before any Ivy descriptor generation tasks"
        }
        val generateIvyModuleDescriptorTask = project.task("generateIvyModuleDescriptor") { Task t ->
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
        val republishTask = if (republishHandler != null) {
            project.task<DefaultTask>("republish") {
                group = "Publishing"
                description = "'Republishes' the artifacts for the module."
            }
        } else {
            null
        }

        // Configure the publish task to deal with the version number, include source dependencies and convert
        // dynamic dependency versions to fixed version numbers.  We do this in a projectsEvaluated block so that
        // source dependency projects have been evaluated, so we can be sure their information is available.
        project.gradle.projectsEvaluated { gradle ->
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

    private fun setupRecordingOfOriginalConfigurationOrder(configurations: ConfigurationContainer) {
        configurations.whenObjectAdded { c ->
            originalConfigurationOrder.add(c.name)
        }
        configurations.whenObjectRemoved { c ->
            originalConfigurationOrder.remove(c.name)
        }
    }

    // Public for use from closure.
    fun createDefaultPublication(
        publishing: PublishingExtension,
        packageArtifactHandlers: NamedDomainObjectContainer<PackageArtifactHandler>
    ) {
        publishing.publications { pubs ->
            val defaultPublication = pubs.maybeCreate(DEFAULT_PUBLICATION_NAME, IvyPublication::class.java) as IvyPublication
            val configureAction: Action<IvyPublication> = Action { pub ->
                // NOTE: We use .each instead of .all here because we have to fix the state of the publication within
                // this closure.  If some other callback adds more configurations or packaged artifacts on the fly
                // later, they won't be included in the publication.

                // Note that the default ivy-publish behaviour in Gradle 1.4 was to add only visible configurations and
                // the ones they extended from (even if those super-configurations were not themselves public).
                for (conf in project.configurations) {
                    if (includeConfiguration(conf)) {
                        pub.configurations { ivyConfs ->
                            val ivyConf = ivyConfs.maybeCreate(conf.name)
                            for (it in conf.extendsFrom) {
                                ivyConf.extend(it.name)
                            }
                        }
                    }
                }

                // Add an artifact to this publication for each handler that has been or is later added.
                for (handler in packageArtifactHandlers) {
                    if (includeConfiguration(project.configurations.getByName(handler.configuration))) {
                        val packageTask = project.tasks.getByName(handler.packageTaskName) as AbstractArchiveTask
                        // Gradle automatically converts Closure to the right Action<> type.
                        //noinspection GroovyAssignabilityCheck
                        pub.artifact(packageTask) { artifact ->
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
    fun configureGenerateDescriptorTasks(
        beforeGenerateDescriptorTask: Task,
        generateIvyModuleDescriptorTask: Task,
        project: Project
    ) {
        project.tasks.withType(GenerateIvyDescriptor::class.java).whenTaskAdded { descriptorTask ->
            generateIvyModuleDescriptorTask.dependsOn(descriptorTask)
            descriptorTask.dependsOn(beforeGenerateDescriptorTask)

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
    fun configureRepublishTaskDependencies(republishTask: Task?) {
        project.tasks.withType(PublishToIvyRepository::class.java).whenTaskAdded { publishTask ->
            if (republishTask != null) {
                republishTask.dependsOn(publishTask)
            }
        }
    }

    // This is public only so that it can be called from within a closure.
    fun includeConfiguration(conf: Configuration): Boolean {
        return conf.visible ||
            this.publishPrivateConfigurations ||
            project.configurations.any { it.extendsFrom conf }
    }

    fun defineCheckTask(packedDependenciesStateSource: PackedDependenciesStateSource) {
        val repubHandler = republishHandler
        if (repubHandler != null) {
            val repoUrl = repubHandler.toRepository
            val repos = project.repositories.matching { repo ->
                repo is AuthenticationSupported && repo.credentials.username != null
            }
            if (repos.size > 0 && repoUrl != null) {
                val repo = repos[0] as AuthenticationSupported
                project.task<CheckPublishedDependenciesTask>("checkPackedDependencies") {
                    group = "Publishing"
                    description = "Check if all packed dependencies are accessible in the target repo."
                    initialize(packedDependenciesStateSource, repoUrl, repo.credentials)
                }
            }
        }
    }

    override val repositories: RepositoryHandler
        get() = throw UnsupportedOperationException(
            "Repositories for publishing can only be configured using a closure, not by accessing the repositories " +
            "collection as a property.  That means, if you have a build script from an older verson of the Holy " +
            "Gradle which has 'publishPackages { repositories.ivy { ... } }' you must change it to " +
            "'publishPackages { repositories { ivy { ... } } }'"
        )

    override fun repositories(configure: Action<RepositoryHandler>) {
        project.publishing {
            repositories { RepositoryHandler handler ->
                configure.execute(handler)
            }
        }
    }

    override fun defaultPublication(configure: Action<Publication>) {
        project.publishing { PublishingExtension publishing ->
            if (!createDefaultPublication) {
                throw RuntimeException(
                    "Cannot configure the default publication because it will not be created, " +
                    "because createDefaultPublication is false."
                )
            }
            configure.execute(publishing.publications.maybeCreate(DEFAULT_PUBLICATION_NAME, IvyPublication))
        }
    }

    override fun republish(closure: Action<RepublishHandler>) {
        if (republishHandler == null) {
            republishHandler = RepublishHandler()
        }
        closure.execute(republishHandler)
    }

    public RepublishHandler getRepublishHandler() {
        if (project != project.rootProject && republishHandler == null) {
            return project.rootProject.publishPackages.republishHandler
        }
        return republishHandler
    }
    
    // Throw an exception if any packed dependencies are marked with noCreateLinkToCache()
    fun failIfPackedDependenciesNotCreatingLink(packedDependencies: Collection<PackedDependencyHandler>) {
        val nonLinkedPackedDependencies =
                packedDependencies.filter { it.shouldUnpackToCache && !it.shouldCreateLinkToCache }
        if (!nonLinkedPackedDependencies.isEmpty()) {
            val dependenciesDescription = (nonLinkedPackedDependencies.map { it.dependencyCoordinate }).joinToString(", ")
            throw RuntimeException(
                "Cannot publish ${project.name} because some packed dependencies are using noCreateLinkToCache(), " +
                "which means Gradle cannot know the real version of those dependencies: [${dependenciesDescription}]"
            )
        }
    }

    // Re-writes the "configurations" element so that its children appear in the same order that the configurations were
    // defined in the project.
    fun putConfigurationsInOriginalOrder(descriptor: IvyModuleDescriptorSpec) {
        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        descriptor.withXml { xml ->
            xml.asNode().configurations.each { confsNode: Node ->
                val confNodes = linkedMapOf<String, Node>()
                originalConfigurationOrder.forEach { confName ->
                    val confNode: Node = confsNode.childNodes.find { it.attribute("name") == confName } as Node
                    // Private configurations will have been removed, and so return null.
                    if (confNode != null) {
                        confsNode.remove(confNode)
                        confNodes[confName] = confNode
                    }
                }
                confNodes.values.forEach { confNode ->
                    confsNode.append(confNode)
                }
            }
        }
    }

    // TODO 2016-07-03 HughG: This should use the source configurations from the dependency XML node
    // instead of searching all configurations.  Otherwise we may get the wrong answer if there's
    // more than one version of the same dependency, in different configurations.
    private /*static*/ fun getDependencyVersion(project: Project, group: String, module: String): String? {
        var version: String? = null
        project.configurations.forEach { conf ->
            conf.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                val ver = artifact.moduleVersion.id
                if (ver.group == group && ver.name == module) {
                    version = ver.version
                }
            }
        }
        return version
    }

    // Replace the version of any dependencies which were specified with dynamic version numbers, so they have fixed
    // version numbers as resolved for the build which is to be published.
    fun /*static*/ freezeDynamicDependencyVersions(project: Project, descriptor: IvyModuleDescriptorSpec) {
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
    fun collapseMultipleConfigurationDependencies(descriptor: IvyModuleDescriptorSpec) {
        // WARNING: This method loses any sub-nodes of each dependency node.  These can be "conf" (not needed, as we use
        // the "@conf" attribute) and "artifact", "exclude", "include" (only needed to override the target's ivy file,
        // which I don't think we care about for now).

        // Pre-calculate the indices for the original configuration order, so we can easily sort the configuration
        // mapping versions by source config, so that the order matches the order in which the configs were defined.
        val configIndices = linkedMapOf<String, Int>()
        originalConfigurationOrder.forEachIndexed { i, entry -> configIndices[entry] = i }

        // IvyModuleDescriptor#withXml doc says Gradle converts Closure to Action<>, so suppress IntelliJ IDEA check
        //noinspection GroovyAssignabilityCheck
        descriptor.withXml { xml ->
            val configsByCoord = linkedMapOf<String, List<String>>().withDefault { mutableListOf() }
            xml.asNode().dependencies.forEach { depsNode ->
                depsNode.dependency.forEach { depNode ->
                    val coord = "${depNode.@org}:${depNode.@name}:${depNode.@rev}"
                    configsByCoord[coord].add(((String)depNode.@conf))
                }
                depsNode.children().clear()
                configsByCoord.forEach { coord, configs ->
                    val c = coord.split(":")
                    val sortedConfigs = configs.sortedWith(Comparator { a: String, b: String ->
                        val aIndex = configIndices[a.split("->")[0]]!!
                        val bIndex = configIndices[b.split("->")[0]]!!
                        aIndex.compareTo(bIndex)
                    })
                    depsNode.appendNode("dependency", mapOf(
                        "org" to c[0],
                        "name" to c[1],
                        "rev" to c[2],
                        "conf" to sortedConfigs.joinToString(";")
                    ))
                }
            }
        }
    }

    fun addConfigurationDependenciesToDefaultPublication(descriptorTask: GenerateIvyDescriptor) {
        descriptorTask.descriptor.withXml { xml ->
            val depsNode = xml.asNode().dependencies.find() as Node
            descriptorTask.project.configurations.each { conf ->
                conf.dependencies.withType(ModuleDependency::class.java).forEach { dep ->
                    depsNode.appendNode("dependency", mapOf(
                        "org" to dep.group,
                        "name" to dep.name,
                        "rev" to dep.version,
                        "conf" to "${conf.name}->${dep.targetConfiguration}"
                    ))
                }
            }
        }
    }

    // Add dependencies to this publication for each ModuleDependency of each Configuration of this Project.
    private fun fixArrowsInXml(ivyDescriptorTask: GenerateIvyDescriptor) {
        // Rewrite "&gt;" to ">" in generated ivy.xml files, for human-readability.
        if (ivyDescriptorTask.didWork) {
            ivyDescriptorTask.outputs.files.files.forEach { ivyFile ->
                var contents = ivyFile.readText()
                contents = contents.replace("&gt;", ">")
                ivyFile.writeText(contents)
            }
        }
    }
}
package holygradle.packaging

import holygradle.Helper
import holygradle.custom_gradle.VersionInfo
import holygradle.custom_gradle.util.CamelCase
import holygradle.gradle.api.lazyConfiguration
import holygradle.io.FileHelper
import holygradle.kotlin.dsl.*
import holygradle.publishing.PublishPackagesExtension
import holygradle.publishing.RepublishHandler
import holygradle.scm.DummySourceControl
import holygradle.scm.SourceControlRepositories
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.*
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.file.CopySpec
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import javax.inject.Inject

open class PackageArtifactHandler @Inject constructor(val project: Project, val name: String) : PackageArtifactDSL {
    override var configuration: String = name
    private val rootPackageDescriptor = PackageArtifactDescriptor(project, ".")
    private val lazyConfigurations = mutableListOf<Action<PackageArtifactDescriptor>>()

    companion object {
        @JvmStatic
        fun createContainer(project: Project): NamedDomainObjectContainer<PackageArtifactHandler> {
            val packageArtifactHandlers = project.container<PackageArtifactHandler> { name ->
                project.objects.newInstance(project, name)
            }
            project.extensions.add("packageArtifacts", packageArtifactHandlers)
            val createPublishNotesTask = defineCreatePublishNotesTask(project)
            val packageEverythingTask = project.task<DefaultTask>("packageEverything") {
                group = "Publishing"
                description = "Creates all zip packages for project '${project.name}'."
            }
            val repackageEverythingTask = project.task<DefaultTask>("repackageEverything") {
                group = "Publishing"
                description = "As 'packageEverything' but doesn't auto-generate any files."
                dependsOn(packageEverythingTask)
            }

            // Create this configuration early so we can create the buildScript PackageArtifactHandler.
            project.configurations.findByName("buildScript") ?: project.configurations.create("buildScript")
            val buildScriptHandler =
                    packageArtifactHandlers.findByName("buildScript") ?: packageArtifactHandlers.create("buildScript")
            buildScriptHandler.include(project.buildFile.name)
            buildScriptHandler.include(project.gradle.startParameter.settingsFile?.name ?: Settings.DEFAULT_SETTINGS_FILE)
            buildScriptHandler.configuration = "buildScript"
            packageArtifactHandlers.all { packArt ->
                val packageTask = packArt.definePackageTask(createPublishNotesTask)
                try {
                    project.artifacts.add(packArt.configuration, packageTask)
                } catch (e: UnknownConfigurationException) {
                    throw RuntimeException(
                            "Failed to find configuration '${packArt.configuration}' in ${project} " +
                            "when adding packageArtifact entry '${packArt.name}'",
                            e
                    )
                }
                packageEverythingTask.dependsOn(packageTask)
            }

            // Hook up the repackage task for this project to the republish task for the root project.  We do this in an
            // afterEvaluate block on the root project in case there's some odd evaluationDependsOn setup and the root
            // project's republish handler hasn't been created yet but will be once it's evaluated.
            project.rootProject.afterEvaluate {
                val publishPackages = project.rootProject.extensions.findByName("publishPackages") as PublishPackagesExtension
                if (publishPackages.republishHandler != null) {
                    val republishTask = project.tasks.findByName("republish")
                    if (republishTask != null) {
                        republishTask.dependsOn(repackageEverythingTask)
                    }
                }
            }

            return packageArtifactHandlers
        }

        private fun defineCreatePublishNotesTask(project: Project): Task {
            // Create an internal 'createPublishNotes' task to create some text files to be included in all
            // released packages.
            return project.task<DefaultTask>("createPublishNotes") {
                group = "Publishing"
                description = "Creates 'build_info' directory which will be included in published packages."
                val buildInfoDir = File(project.projectDir, "build_info")

                // Save the build directory to an extension so it can be accessed from outside
                val ext = this.extensions.findByName("ext") as ExtraPropertiesExtension
                ext["buildInfoDir"] = buildInfoDir

                onlyIf {
                    // Don't do anything if we are republishing, or we will end up deleting the original build_info and
                    // replacing it with nearly no info.
                    val republishTask = project.tasks.findByName("republish")
                    val republishing = (republishTask != null) && project.gradle.taskGraph.hasTask(republishTask)
                    !republishing
                }
                doLast {
                    FileHelper.ensureDeleteDirRecursive(buildInfoDir)
                    FileHelper.ensureMkdirs(buildInfoDir)

                    val buildNumber = System.getenv("BUILD_NUMBER")
                    if (buildNumber != null) {
                        File(buildInfoDir, "build_number.txt").writeText(buildNumber)
                    }

                    val buildUrl = System.getenv("BUILD_URL")
                    if (buildUrl != null) {
                        File(buildInfoDir, "build_url.txt").writeText(buildUrl)
                    }

                    val versionInfoExtension = project.extensions.findByName("versionInfo") as? VersionInfo
                    if (versionInfoExtension != null) {
                        versionInfoExtension.writeFile(File(buildInfoDir, "versions.txt"))
                    }
                    addSourceRepositoryInfo(this, buildInfoDir)
                    val allSourceDependencies = findAllSourceDependencies(project)
                    val sourceDependenciesDir = File(buildInfoDir, "source_dependencies")
                    FileHelper.ensureMkdirs(sourceDependenciesDir)
                    for (handler in allSourceDependencies) {
                        addSourceRepositoryInfo(this, sourceDependenciesDir, handler)
                    }
                }
            }
        }

        /**
         * Collect all source dependencies keyed by absolute path, because the same repo might be a dependency of
         * several projects in the project graph, each maybe using a different relative path to end up at the same
         * place.  (If they use equivalent paths to depend on different repos, behaviour is undefined!)
         *
         * @param project The project whose set of transitive source dependencies is to be returned.
         * @return The set of transitive source dependencies for the {@code project}
         */
        private fun findAllSourceDependencies(project: Project): Collection<SourceDependencyHandler> {
            val handlers = mutableMapOf<String, SourceDependencyHandler>()
            collectAllSourceDependencies(project, handlers)
            return handlers.values
        }

        private fun collectAllSourceDependencies(
                project: Project,
                handlers: MutableMap<String, SourceDependencyHandler>
        ) {
            val sourceDependencies: NamedDomainObjectContainer<SourceDependencyHandler> by project.extensions
            for (handler in sourceDependencies) {
                handlers[handler.absolutePath.canonicalPath] = handler
                val srcDepProject = handler.sourceDependencyProject
                if (srcDepProject != null) {
                    collectAllSourceDependencies(srcDepProject, handlers)
                }
            }
        }

        private fun addSourceRepositoryInfo(
            createPublishNotesTask: Task,
            baseDir: File,
            handler: SourceDependencyHandler? = null
        ) {
            val project = createPublishNotesTask.project
            // We create a new SourceControlRepository instead of trying to get the "sourceControl" extension from the
            // project, because we don't want a DummySourceControl if there's no SCM info here.  Also, some source
            // dependencies may not have a project.
            //
            // For the originating project we will be passed a null handler (because we're looking at it as the root which
            // has source dependencies, not a source dependency itself), in which case we want that originating project,
            // which we have from the task.
            val sourceRepo = if (handler == null) {
                SourceControlRepositories.create(project)
            } else {
                SourceControlRepositories.create(handler)
            }

            if (sourceRepo !is DummySourceControl) {
                val sourceDepInfoDir = if (handler == null) {
                    // We're adding info for the originating project, so put it at top-level: baseDir = "build_info"
                    baseDir
                } else {
                    // We're adding info for a subproject source dependency, so put it in a sub-folder; in this case,
                    // baseDir = "build_info/source_dependencies"
                    File(baseDir, handler.targetName).apply {
                        FileHelper.ensureMkdirs(this)
                    }
                }

                File(sourceDepInfoDir, "source_url.txt").writeText(sourceRepo.url)
                val revision = sourceRepo.revision
                if (revision != null) {
                    File(sourceDepInfoDir, "source_revision.txt").writeText(revision)
                }
                // The path from the createPublishNotes task's project to the source repository.
                val relativePath = if (handler == null) {
                    "."
                } else {
                    Helper.relativizePath(handler.absolutePath, project.projectDir)
                }
                File(sourceDepInfoDir, "source_path.txt").writeText(relativePath)
            }
        }
    }

    override fun include(vararg patterns: String): PackageArtifactIncludeHandler {
        return rootPackageDescriptor.include(*patterns)
    }

    override fun include(pattern: String, action: Action<PackageArtifactIncludeHandler>) {
        rootPackageDescriptor.include(pattern, action)
    }

    override fun includeBuildScript(action: Action<PackageArtifactBuildScriptHandler>) {
        rootPackageDescriptor.includeBuildScript(action)
    }

    override fun includeTextFile(path: String, action: Action<PackageArtifactPlainTextFileHandler>) {
        rootPackageDescriptor.includeTextFile(path, action)
    }

    override fun includeSettingsFile(action: Action<PackageArtifactSettingsFileHandler>) {
        rootPackageDescriptor.includeSettingsFile(action)
    }

    override var createDefaultSettingsFile: Boolean
        get() = rootPackageDescriptor.createDefaultSettingsFile
        set(value) { rootPackageDescriptor.createDefaultSettingsFile = value }

    override fun exclude(vararg patterns: String) {
        rootPackageDescriptor.exclude(*patterns)
    }

    override fun from(fromLocation: String) {
        rootPackageDescriptor.from(fromLocation)
    }

    override fun from(fromLocation: String, action: Action<PackageArtifactBaseDSL>) {
        rootPackageDescriptor.from(fromLocation, action)
    }

    override fun to(toLocation: String) {
        rootPackageDescriptor.to(toLocation)
    }

    val packageTaskName: String = CamelCase.build("package", name)

    private fun doConfigureCopySpec(descriptor: PackageArtifactDescriptor, copySpec: CopySpec) {
        for (includeHandler in descriptor.includeHandlers) {
            var fromDir = project.projectDir
            if (descriptor.fromLocation != ".") {
                fromDir = File(fromDir, descriptor.fromLocation)
            }
            copySpec.from(fromDir) { spec ->
                spec.setIncludes(includeHandler.includePatterns)
                spec.setExcludes(descriptor.excludes)
            }
        }
        for (fromDescriptor in descriptor.fromDescriptors) {
            doConfigureCopySpec(fromDescriptor, copySpec)
        }
    }

    fun configureCopySpec(copySpec: CopySpec) {
        doConfigureCopySpec(rootPackageDescriptor, copySpec)
    }

    fun definePackageTask(createPublishNotesTask: Task?): AbstractArchiveTask {
        val taskName = packageTaskName
        val t = project.task<Zip>(taskName)
        t.description = "Creates a zip file for '${name}' in preparation for publishing project '${project.name}'."
        t.inputs.property("version", project.version)
        if (createPublishNotesTask != null) {
            t.dependsOn(createPublishNotesTask)
        }
        t.baseName = project.name + "-" + name
        t.destinationDir = File(project.projectDir, "packages")
        t.includeEmptyDirs = false

        t.lazyConfiguration {
            // t.group = "Publishing " + project.name
            // t.classifier = name
            for (action in lazyConfigurations) {
                action.execute(rootPackageDescriptor)
            }

            from(project.projectDir) {
                include("build_info/**")
            }

            val taskDir = File(destinationDir, taskName)
            FileHelper.ensureMkdirs(taskDir, "as output folder for publish notes ${taskDir}")

            // If we're publishing then let's generate the auto-generatable files. But if we're 'republishing'
            // then just make sure that all auto-generated files are present.
            val repackageTask = project.tasks.findByName("repackageEverything")
            var republishHandler: RepublishHandler? = null
            if (repackageTask != null && project.gradle.taskGraph.hasTask(repackageTask)) {
                val publishPackages = project.extensions.findByName("publishPackages") as PublishPackagesExtension
                republishHandler = publishPackages.republishHandler
                doFirst {
                    rootPackageDescriptor.processPackageFiles(taskDir)
                }
            } else {
                doFirst {
                    rootPackageDescriptor.createPackageFiles(taskDir)
                }
            }

            rootPackageDescriptor.configureZipTask(t, taskDir, republishHandler)
        }

        t.doLast {
            println("Created '${t.archiveName}'.")
        }
        return t
    }
}

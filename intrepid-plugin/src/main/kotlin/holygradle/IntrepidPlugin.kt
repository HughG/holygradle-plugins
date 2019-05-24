package holygradle

import holygradle.artifacts.*
import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.CustomGradleCorePlugin
import holygradle.custom_gradle.PrerequisitesExtension
import holygradle.custom_gradle.util.ProfilingHelper
import holygradle.dependencies.*
import holygradle.gradle.api.executeLazyConfiguration
import holygradle.io.FileHelper
import holygradle.io.Link
import holygradle.kotlin.dsl.*
import holygradle.links.LinkHandler
import holygradle.links.LinkTask
import holygradle.links.LinksToCacheTask
import holygradle.packaging.PackageArtifactHandler
import holygradle.publishing.DefaultPublishPackagesExtension
import holygradle.publishing.PublishPackagesExtension
import holygradle.scm.SourceControlRepositories
import holygradle.source_dependencies.RecursivelyFetchSourceTask
import holygradle.source_dependencies.SourceDependenciesStateHandler
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.source_dependencies.SourceDependencyTaskHandler
import holygradle.unpacking.GradleZipHelper
import holygradle.unpacking.PackedDependenciesStateHandler
import holygradle.unpacking.SevenZipHelper
import holygradle.unpacking.SpeedyUnpackManyTask
import org.gradle.api.*
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.logging.LogLevel
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import java.io.File
import javax.inject.Inject

class IntrepidPlugin @Inject constructor() : Plugin<Project> {
    companion object {
        @JvmStatic
        val EVERYTHING_CONFIGURATION_NAME = "everything"
        @JvmStatic
        val EVERYTHING_CONFIGURATION_PROPERTY = "createEverythingConfiguration"
        @JvmStatic
        val FETCH_ALL_DEPENDENCIES_TASK_NAME = "fetchAllDependencies"
    }

    override fun apply(project: Project) {
        val profilingHelper = ProfilingHelper(project.logger)
        val timer = profilingHelper.startBlock("IntrepidPlugin#apply(${project})")

        /**************************************
         * Apply other plugins
         **************************************/
        project.apply<IvyPublishPlugin>()
        
        project.apply<CustomGradleCorePlugin>()

        /**************************************
         * Prerequisites
         **************************************/
        /*val prerequisites =*/ project.extensions.findByName("prerequisites") as? PrerequisitesExtension
            ?: throw RuntimeException("Failed to find PrerequisitesExtension")

        /**************************************
         * Configurations and ConfigurationSets
         **************************************/
        // Mark configurations whose name begins with "private" as private in Ivy terms.
        project.configurations.whenObjectAdded { conf ->
            if (conf.name.startsWith("private")) {
                conf.isVisible = false
            }
        }

        // Set the default version conflict behaviour to failure, unless explicitly overridden in the script, or unless
        // we're running one of the standard tasks used to resolve version conflicts.
        val dependenciesSettings = DependenciesSettingsHandler.findOrCreateDependenciesSettings(project)
        val configurations = project.configurations
        val baseTaskNames = project.gradle.startParameter.taskNames.map { it.split(":").last() }
        val runningDependencyTask = listOf("dependencies", "dependencyInsight").any { baseTaskNames.contains(it) }
        // We call failOnVersionConflict inside projectsEvaluated so that the script can set
        // dependenciesSettings.defaultFailOnVersionConflict at any point, instead of having to be careful to call it
        // before any configurations are added, which would be a surprising constraint--at least, it surprised me,
        // coming back to this option after a couple of months.  We don't just use project.afterEvaluate because the
        // default involves falling back to the root project's setting.
        project.gradle.projectsEvaluated {
            configurations.all { conf ->
                if (dependenciesSettings.defaultFailOnVersionConflict && !runningDependencyTask) {
                    conf.resolutionStrategy.failOnVersionConflict()
                }
            }
        }

        val configurationSetTypes = project.container<ConfigurationSetType> { name ->
            project.objects.newInstance<DefaultConfigurationSetType>(name)
        }
        project.extensions.add("configurationSetTypes", configurationSetTypes)
        configurationSetTypes.addAll(DefaultVisualStudioConfigurationSetTypes.TYPES.values)
        configurationSetTypes.addAll(DefaultWebConfigurationSetTypes.TYPES.values)

        project.extensions.add("configurationSets", project.container<ConfigurationSet> { name ->
            project.objects.newInstance<ProjectConfigurationSet>(name, project)
        })
  
        /**************************************
         * Properties
         **************************************/
        // The first properties are common to all projects, but the unpackModules collection is per-project.
        project.rootProject.extra["svnConfigPath"] = System.getenv("APPDATA") + "/Subversion"

        /**************************************
         * DSL extensions
         **************************************/

        // Define the 'packedDependenciesSettingsHandler' DSL for the project (used by BuildScriptDependencies).
        /*PackedDependenciesSettingsHandler packedDependencySettings =*/
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project)

        // Prepare dependencies that this plugin will require.
        val buildScriptDependencies = BuildScriptDependencies.initialize(project)
        // Only define the build script dependencies for the root project because they're shared across all projects.
        if (project == project.rootProject) {
            buildScriptDependencies.add("sevenZip", true, true)
            buildScriptDependencies.add("credential-store")
        }

        // Define the 'packedDependency' DSL for the project.
        /*val packedDependencies =*/ PackedDependencyHandler.createContainer(project)

        // Define the 'sourceOverrides' DSL for the project
        /*val sourceOverrides =*/ SourceOverrideHandler.createContainer(project)

        // Define the 'dependenciesState' DSL for the project.
        /*val dependenciesState =*/ DependenciesStateHandler.createExtension(project)

        // Define the 'dependenciesState' DSL for the project's build script.
        /*val buildscriptDependenciesState =*/ DependenciesStateHandler.createExtension(project, true)

        // Define the 'packedDependenciesState' DSL for the project.
        val packedDependenciesState = PackedDependenciesStateHandler.createExtension(project)

        // DSL extension to specify source dependencies
        val sourceDependencies = SourceDependencyHandler.createContainer(project)

        // Define the 'packedDependenciesState' DSL for the project.
        val sourceDependenciesState = SourceDependenciesStateHandler.createExtension(project)

        // Define 'packageArtifacts' DSL for the project.
        /*val packageArtifactHandlers =*/ PackageArtifactHandler.createContainer(project)

        // Define 'publishPackages' DSL block.
        /*val publishPackagesExtension =*/ DefaultPublishPackagesExtension.getOrCreateExtension(project)

        // Define 'sourceControl' DSL.
        SourceControlRepositories.createExtension(project)
        
        // Define 'links' DSL block.
        val links = LinkHandler.createExtension(project)
        
        // Define 'sourceDependencyTasks' DSL
        val sourceDependencyTasks = SourceDependencyTaskHandler.createContainer(project)

        /**************************************
         * Tasks
         **************************************/
        val unpackDependenciesTask = project.task<SpeedyUnpackManyTask>("extractPackedDependencies") {
            group = "Dependencies"
            description = "Extract all packed dependencies, to the unpack cache or to the workspace"
            val sevenZipHelper = SevenZipHelper(project)
            if (sevenZipHelper.isUsable) {
                initialize(sevenZipHelper)
            } else {
                initialize(GradleZipHelper(project))
            }
        }

        val deleteLinksToCacheTask = project.task<LinksToCacheTask>("deleteLinksToCache") {
            group = "Dependencies"
            description = "Delete all links to the unpack cache"
        }
        deleteLinksToCacheTask.initialize(LinksToCacheTask.Mode.CLEAN)
        val rebuildLinksToCacheTask = project.task<LinksToCacheTask>("rebuildLinksToCache") {
            group = "Dependencies"
            description = "Rebuild all links to the unpack cache"
            // We need to make sure we unpack before trying to create a link to the cache, or we will end up failing to
            // create directory junctions, or with broken file symlinks instead of working directory symlinks.
            dependsOn(unpackDependenciesTask)
        }
        rebuildLinksToCacheTask.initialize(LinksToCacheTask.Mode.BUILD)

        val fetchAllSourceDependenciesTask = project.task<RecursivelyFetchSourceTask>("fetchAllSourceDependencies") {
            group = "Dependencies"
            description = "Retrieves all 'sourceDependencies' recursively."
            recursiveTaskName = FETCH_ALL_DEPENDENCIES_TASK_NAME
        }

        val deleteLinksTask = project.task<DefaultTask>("deleteLinks") {
            group = "Dependencies"
            description = "Remove all links."
            dependsOn(deleteLinksToCacheTask)
        }
        val rebuildLinksTask = project.task<LinkTask>("rebuildLinks") {
            group = "Dependencies"
            description = "Rebuild all links."
            dependsOn(rebuildLinksToCacheTask)
            // Some links might be to other source folders, so make sure we fetch them first.
            dependsOn(fetchAllSourceDependenciesTask)
        }
        rebuildLinksTask.initialize()

        /*val fetchAllDependenciesTask =*/ project.task<DefaultTask>(FETCH_ALL_DEPENDENCIES_TASK_NAME) {
            group = "Dependencies"
            description = "Retrieves all 'packedDependencies' and 'sourceDependencies', and sets up necessary links."
            dependsOn(rebuildLinksTask)
        }
        val fetchFirstLevelSourceDependenciesTask = project.task<RecursivelyFetchSourceTask>(
            "fetchFirstLevelSourceDependencies"
        ) {
            group = "Dependencies"
            description = "Retrieves only the first level 'sourceDependencies'."
        }
        val beforeFetchSourceDependenciesTask = project.task<DefaultTask>("beforeFetchSourceDependencies") {
            group = "Dependencies"
            description = "Runs before source dependencies are fetched, to check authorisation setup. " +
                "Extend it with doLast if needed."
        }
        /*SummariseAllDependenciesTask allDependenciesTask =*/ project.task<SummariseAllDependenciesTask>(
                "summariseAllDependencies"
        ) {
            group = "Dependencies"
            description = "Create an XML file listing all direct and transitive dependencies"
            initialize()
        }

        // Lazy configuration is a "secret" internal feature for use by plugins.  If a task adds a ".ext.lazyConfiguration"
        // property containing a single Closure, it will be executed just before that specific task runs.  As long as
        // intrepid-plugin is applied in a build script, this is available to all plugins.
        project.rootProject.gradle.taskGraph.beforeTask { task ->
            task.executeLazyConfiguration()
        }

        /**************************************
         * Packaging and publishing stuff
         **************************************/

        if (project.hasProperty(EVERYTHING_CONFIGURATION_PROPERTY)) {
            // Define an 'everything' configuration which depends on all other configurations.
            val everythingConf = configurations.maybeCreate(EVERYTHING_CONFIGURATION_NAME)
            project.gradle.projectsEvaluated {
                for (conf in configurations) {
                    if (conf.name != EVERYTHING_CONFIGURATION_NAME && conf.isVisible) {
                        everythingConf.extendsFrom(conf)
                    }
                }
            }
        }

        /**************************************
         * Source dependencies
         **************************************/        
        project.gradle.projectsEvaluated {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for tasks for sourceDependency versions") {
                val buildTasks = Helper.getProjectBuildTasks(project)

                // For each source dependency, create a suitable task and link it into the
                // fetchAllDependencies task.
                for (sourceDep in sourceDependencies) {
                    val fetchTask = sourceDep.createFetchTask(project)
                    fetchTask.dependsOn(beforeFetchSourceDependenciesTask)
                    fetchAllSourceDependenciesTask.dependsOn(fetchTask)
                    fetchFirstLevelSourceDependenciesTask.dependsOn(fetchTask)

                    // Set up build task dependencies.
                    val depProject = project.findProject(":${sourceDep.name}")
                    if (depProject != null) {
                        val subBuildTasks = Helper.getProjectBuildTasks(depProject)
                        for ((taskName, task) in buildTasks) {
                            if (subBuildTasks.containsKey(taskName)) {
                                task.dependsOn(subBuildTasks[taskName])
                            }
                        }
                    }
                }
            }
        }
        
        /**************************************
         * Source dependency commands
         **************************************/
        project.gradle.projectsEvaluated {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for sourceDependencyTasks") {
                // NOTE 2013-11-07 HughG: Possible performance improvement: I think we only need to do this for the root
                // project, since we're visiting things transitively.

                // Need to do this in projectsEvaluated so that all source dependencies and configurations are ready.

                // Define the tasks for source-dependency projects
                val sourceDeps = Helper.getTransitiveSourceDependencies(project)
                for (sourceDep in sourceDeps) {
                    val sourceDepProj = sourceDep.sourceDependencyProject
                    if (sourceDepProj != null) {
                        for (command in sourceDependencyTasks) {
                            command.defineTask(sourceDepProj)
                        }
                    }
                }

                // Define any tasks for this project.
                for (command in sourceDependencyTasks) {
                    command.defineTask(project)
                }
            }
        }
         
        /**************************************
         * Create links
         **************************************/
        project.gradle.projectsEvaluated {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for links tasks") {
                links.mappings.forEach {
                    val linkDir = File(project.projectDir, it.linkPath)
                    val targetDir = File(project.projectDir, it.targetPath)
                    rebuildLinksTask.addLink(linkDir, targetDir)
                    deleteLinksTask.doLast {
                        Link.delete(linkDir)
                    }
                }
            }
        }
        
        /**************************************
         * Unpacking stuff
         **************************************/

        if (project == project.rootProject) {
            val collectDependenciesTask = project.task<CollectDependenciesTask>("collectDependencies") {
                group = "Dependencies"
                description = "Collect all non-source dependencies into a '${CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME}' folder."
            }
            profilingHelper.timing("IntrepidPlugin(${project}) configure collectDependenciesTask") {
                collectDependenciesTask.initialize()
            }
            val zipDependenciesTask = project.task<ZipDependenciesTask>("zipDependencies") {
                group = "Dependencies"
                description = "Collect all non-source dependencies into a '${CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME}' ZIP."
            }
            profilingHelper.timing("IntrepidPlugin(${project}) configure zipDependenciesTask") {
                zipDependenciesTask.initialize()
            }
        }

        project.gradle.projectsEvaluated {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for populating link-to-cache tasks") {
                // Initialize for links from workspace to the unpack cache, for each dependency which was unpacked to
                // the unpack cache (as opposed to unpacked directly to the workspace).
                //
                // Need to do this in projectsEvaluated so we can be sure that all packed dependencies have been set up.
                deleteLinksToCacheTask.addUnpackModuleVersions(packedDependenciesState)
                rebuildLinksToCacheTask.addUnpackModuleVersions(packedDependenciesState)
            }

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for populating pathsForPackedDependencies") {
                unpackDependenciesTask.addUnpackModuleVersions(packedDependenciesState)
            }

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for checkPackedDependencies") {
                val publishPackages = project.extensions["publishPackages"] as PublishPackagesExtension
                publishPackages.defineCheckTask(packedDependenciesState)
            }
        }

        project.afterEvaluate {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for cross-project link task dependencies") {
                // Make the link creation in this project depend on that in its source dependencies.  This makes it
                // possible to create links (using the "links" DSL) which point to links in other projects.  Without
                // this dependency, the target links might not have been created when this project tries to create links
                // to them.
                //
                // Need to do this in project.afterEvaluate so we can be sure that all configurations and source
                // dependencies have been set up but we don't need to know about subprojects' source dependencies).
                for (conf in sourceDependenciesState.getAllConfigurationsPublishingSourceDependencies()) {
                    rebuildLinksTask.dependsOn(conf.getTaskDependencyFromProjectDependency(true, rebuildLinksTask.name))
                    deleteLinksTask.dependsOn(conf.getTaskDependencyFromProjectDependency(true, deleteLinksTask.name))
                }
            }
        }

        profilingHelper.timing("IntrepidPlugin(${project}) set up source overrides") {
            setupSourceOverrides(project)
        }

        timer.endBlock()
    }

    private fun setupSourceOverrides(project: Project) {
        var addedSourceOverrideDummyModulesRepo = false
        val sourceOverrides: NamedDomainObjectContainer<SourceOverrideHandler> by project.extensions

        sourceOverrides.whenObjectAdded {
            // Fail if source overrides are added in a non-root project, because it will be even more effort to tie together
            // overrides across subprojects and cross-check them.  We'd like to support that some day, maybe.  We could just
            // not add the DSL handler to non-root projects, but then the user would only get a MissingMethodException
            // instead of a useful error message.
            if (project != project.rootProject) {
                throw RuntimeException("Currently sourceOverrides can only be used in the root project.")
            }

            // If and when any source overrides are added, we also need to add a repo to contain dummy entries for all the
            // overridden module versions.  We force this to the start of the project repo list because it will contain
            // relatively few entries, and those versions won't appear in any normal repo.
            if (!addedSourceOverrideDummyModulesRepo) {
                val tempDir = File(project.buildDir, "holygradle/source_override")
                FileHelper.ensureMkdirs(tempDir)
                val sourceOverrideDummyModulesRepo = project.repositories.ivy { it.url = tempDir.toURI() }
                project.repositories.remove(sourceOverrideDummyModulesRepo)
                project.repositories.addFirst(sourceOverrideDummyModulesRepo)
                addedSourceOverrideDummyModulesRepo = true
            }
        }

        // Add a dependency resolve rule for all configurations, which applies source overrides (if there are any).  We
        // use ConfigurationContainer#all so that this is applied for each configuration when it's created.
        project.configurations.all { configuration ->
            // Gradle automatically converts Closures to Action<>, so ignore IntelliJ warning.
            //noinspection GroovyAssignabilityCheck
            configuration.resolutionStrategy.eachDependency { details ->
                project.logger.debug(
                        "Checking for source overrides: requested ${details.requested}, target ${details.target}, " +
                                "in ${configuration}"
                )
                for (handler in sourceOverrides) {
                    if (shouldUseSourceOverride(project, details, handler)) {
                        details.useVersion(handler.dummyVersionString)
                        break
                    }
                }
            }
        }

        project.gradle.addListener(SourceOverridesDependencyResolutionListener(project))

        if (project == project.rootProject) {
            // We use taskGraph.whenReady here, instead of just project.afterEvaluate, because we want to know whether
            // the "dependencies" task was selected.
            project.gradle.taskGraph.whenReady {
                if (!sourceOverrides.isEmpty()) {
                    // If the user is running the 'dependencies' task then it may because they have a depdendency conflict, so
                    // make sure they get the details of sourceOverrides regardless of the configured logging level.
                    val level = if (project.gradle.taskGraph.hasTask("dependencies")) LogLevel.LIFECYCLE else LogLevel.INFO
//2345678901234567890123456789012345678901234567890123456789012345678901234567890 <-- 80-column ruler
                    project.logger.log(
                            level,
                            """
This project has source overrides, which have automatically-generated versions:
"""
                    )
                    for (override in sourceOverrides) {
                        project.logger.log(level, "  ${override.dependencyCoordinate} -> ${override.dummyDependencyCoordinate}")
                        project.logger.log(level, "    at ${override.from}")
                    }
                }
            }
        }
    }

    private fun shouldUseSourceOverride(
            project: Project,
            details: DependencyResolveDetails,
            handler: SourceOverrideHandler
    ): Boolean {
        // If there's a dependency conflict, the 'requested' version may not be the one in the source
        // override handler, even though the current 'target' version is, so check both.
        if (details.requested.group == handler.groupName &&
                details.requested.name == handler.dependencyName &&
                details.requested.version == handler.versionStr
        ) {
            project.logger.debug("  MATCH requested: using ${handler.dummyVersionString}")
            return true
        }
        if (details.target.group == handler.groupName &&
                details.target.name == handler.dependencyName &&
                details.target.version == handler.versionStr
        ) {
            project.logger.debug("  MATCH target: using ${handler.dummyVersionString}")
            return true
        }
        if (details.target.group == handler.groupName &&
                details.target.name == handler.dependencyName &&
                details.target.version.endsWith("+")
        ) {
            project.logger.debug(
                    "  MATCH FLOATING version for source override ${handler.name}: using ${handler.dummyVersionString}"
            )
            return true
        }
        return false
    }

}


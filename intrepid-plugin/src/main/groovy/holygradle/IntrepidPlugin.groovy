package holygradle

import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.CustomGradleCorePlugin
import holygradle.custom_gradle.PrerequisitesChecker
import holygradle.custom_gradle.PrerequisitesExtension
import holygradle.custom_gradle.util.ProfilingHelper
import holygradle.custom_gradle.util.Symlink
import holygradle.dependencies.CollectDependenciesTask
import holygradle.dependencies.DependenciesStateHandler
import holygradle.dependencies.DependencySettingsExtension
import holygradle.dependencies.PackedDependencyHandler
import holygradle.packaging.PackageArtifactHandler
import holygradle.publishing.DefaultPublishPackagesExtension
import holygradle.publishing.PublishPackagesExtension
import holygradle.scm.SourceControlRepositories
import holygradle.source_dependencies.*
import holygradle.symlinks.SymlinkHandler
import holygradle.symlinks.SymlinkTask
import holygradle.symlinks.SymlinksToCacheTask
import holygradle.unpacking.*
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.publish.PublishingExtension

public class IntrepidPlugin implements Plugin<Project> {
    void apply(Project project) {
        ProfilingHelper profilingHelper = new ProfilingHelper(project.logger)
        def timer = profilingHelper.startBlock("IntrepidPlugin#apply(${project})")

        /**************************************
         * Apply other plugins
         **************************************/
        project.apply plugin: 'ivy-publish'
        
        project.apply plugin: CustomGradleCorePlugin.class

        /**************************************
         * Prerequisites
         **************************************/
        PrerequisitesExtension prerequisites = project.extensions.findByName("prerequisites") as PrerequisitesExtension
        if (prerequisites != null) {
            prerequisites.specify("HgAuth", { PrerequisitesChecker checker -> Helper.checkHgAuth(checker)})
        }
    
        /**************************************
         * Configurations
         **************************************/
        DependencySettingsExtension dependencySettings =
            DependencySettingsExtension.findOrCreateDependencySettings(project)
        final ConfigurationContainer configurations = project.configurations
        final List<String> baseTaskNames = project.gradle.startParameter.taskNames.collect { it.split(":").last() }
        final boolean runningDependencyTask = ["dependencies", "dependencyInsight"].any { baseTaskNames.contains(it) }
        // We call failOnVersionConflict inside Configurations#all, because that's called lazily when configurations are
        // added, so lets the script set dependencySettings.defaultFailOnVersionConflict before adding any.
        configurations.all { Configuration it ->
            if (dependencySettings.defaultFailOnVersionConflict && !runningDependencyTask) {
                it.resolutionStrategy.failOnVersionConflict()
            }
        }
  
        /**************************************
         * Properties
         **************************************/
        // The first properties are common to all projects, but the unpackModules collection is per-project.
        project.rootProject.ext.svnConfigPath = System.getenv("APPDATA") + "/Subversion"
        project.rootProject.ext.unpackedDependenciesCache = new File(project.gradle.gradleUserHomeDir, "unpackCache")

        /**************************************
         * DSL extensions
         **************************************/
        // Prepare dependencies that this plugin will require.
        BuildScriptDependencies buildScriptDependencies = BuildScriptDependencies.initialize(project)
        // Only define the build script dependencies for the root project because they're shared across all projects.
        if (project == project.rootProject) {
            // 7zip
            buildScriptDependencies.add("sevenZip", true)

            // Mercurial
            buildScriptDependencies.add("Mercurial", true)
            buildScriptDependencies.add("credential-store")
            if (buildScriptDependencies.getPath("Mercurial") != null) {
                Task hgUnpackTask = buildScriptDependencies.getUnpackTask("Mercurial")
                hgUnpackTask.doLast {
                    Helper.addMercurialKeyringToIniFile(buildScriptDependencies.getPath("Mercurial"))
                }
            }
        }

        // Define the 'packedDependency' DSL for the project.
        Collection<PackedDependencyHandler> packedDependencies = PackedDependencyHandler.createContainer(project)

        // Define the 'dependenciesState' DSL for the project.
        DependenciesStateHandler dependenciesState = DependenciesStateHandler.createExtension(project)

        // Define the 'dependenciesState' DSL for the project's build script.
        DependenciesStateHandler buildscriptDependenciesState = DependenciesStateHandler.createExtension(project, true)

        // Define the 'packedDependenciesState' DSL for the project.
        PackedDependenciesStateHandler packedDependenciesState = PackedDependenciesStateHandler.createExtension(project)

        // DSL extension to specify source dependencies
        Collection<SourceDependencyHandler> sourceDependencies = SourceDependencyHandler.createContainer(project)

        // Define the 'packedDependenciesState' DSL for the project.
        SourceDependenciesStateHandler sourceDependenciesState = SourceDependenciesStateHandler.createExtension(project)

        // Define 'publishPackages' DSL block.
        PublishingExtension publishingExtension = project.extensions.getByType(PublishingExtension)
        project.extensions.create(
            "publishPackages", DefaultPublishPackagesExtension, project, publishingExtension, sourceDependencies, packedDependencies
        )
        
        // Define 'sourceControl' DSL.
        SourceControlRepositories.createExtension(project)
        
        // Define 'symlinks' DSL block.
        SymlinkHandler symlinks = SymlinkHandler.createExtension(project)
        
        // Define 'packageArtifacts' DSL for the project.
        PackageArtifactHandler.createContainer(project)
        
        // Define 'copyArtifacts' DSL
        CopyArtifactsHandler copyArtifacts = CopyArtifactsHandler.createExtension(project)
        
        // Define 'sourceDependencyTasks' DSL
        Collection<SourceDependencyTaskHandler> sourceDependencyTasks = SourceDependencyTaskHandler.createContainer(project)

        /**************************************
         * Tasks
         **************************************/
        SpeedyUnpackManyTask unpackDependenciesTask = (SpeedyUnpackManyTask)project.task(
            "extractPackedDependencies", type: SpeedyUnpackManyTask
        ) { SpeedyUnpackManyTask it ->
            it.group = "Dependencies"
            it.description = "Extract all packed dependencies, to the unpack cache or to the workspace"
            final SevenZipHelper sevenZipHelper = new SevenZipHelper(project)
            if (sevenZipHelper.isUsable) {
                it.initialize(sevenZipHelper)
            } else {
                it.initialize(new GradleZipHelper(project))
            }
        }

        SymlinksToCacheTask deleteSymlinksToCacheTask = (SymlinksToCacheTask)project.task(
            "deleteSymlinksToCache", type: SymlinksToCacheTask
        ) { Task it ->
            it.group = "Dependencies"
            it.description = "Delete all symlinks to the unpack cache"
        }
        deleteSymlinksToCacheTask.initialize(SymlinksToCacheTask.Mode.CLEAN)
        SymlinksToCacheTask rebuildSymlinksToCacheTask = (SymlinksToCacheTask)project.task(
            "rebuildSymlinksToCache", type: SymlinksToCacheTask
        ) { Task it ->
            it.group = "Dependencies"
            it.description = "Rebuild all symlinks to the unpack cache"
            // We need to make sure we unpack before trying to create a symlink to the cache, or we will end up with
            // broken file symlinks, instead of working directory symlinks.
            it.dependsOn unpackDependenciesTask
        }
        rebuildSymlinksToCacheTask.initialize(SymlinksToCacheTask.Mode.BUILD)

        final FETCH_ALL_DEPENDENCIES_TASK_NAME = "fetchAllDependencies"
        RecursivelyFetchSourceTask fetchAllSourceDependenciesTask = (RecursivelyFetchSourceTask)project.task(
            "fetchAllSourceDependencies",
            type: RecursivelyFetchSourceTask
        ) { RecursivelyFetchSourceTask it ->
            it.group = "Dependencies"
            it.description = "Retrieves all 'sourceDependencies' recursively."
            it.recursiveTaskName = FETCH_ALL_DEPENDENCIES_TASK_NAME
        }

        Task deleteSymlinksTask = project.task("deleteSymlinks", type: DefaultTask) { Task it ->
            it.group = "Dependencies"
            it.description = "Remove all symlinks."
            it.dependsOn deleteSymlinksToCacheTask
        }
        SymlinkTask rebuildSymlinksTask = (SymlinkTask)project.task("rebuildSymlinks", type: SymlinkTask) { Task it ->
            it.group = "Dependencies"
            it.description = "Rebuild all symlinks."
            it.dependsOn rebuildSymlinksToCacheTask
            // Some symlinks might be to other source folders, so make sure we fetch them first.
            it.dependsOn fetchAllSourceDependenciesTask
        }
        rebuildSymlinksTask.initialize()

        Task fetchAllDependenciesTask = project.task(
            FETCH_ALL_DEPENDENCIES_TASK_NAME,
            type: DefaultTask
        ) { Task it ->
            it.group = "Dependencies"
            it.description = "Retrieves all 'packedDependencies' and 'sourceDependencies', and sets up necessary symlinks."
            it.dependsOn rebuildSymlinksTask
        }
        RecursivelyFetchSourceTask fetchFirstLevelSourceDependenciesTask = (RecursivelyFetchSourceTask)project.task(
            "fetchFirstLevelSourceDependencies",
            type: RecursivelyFetchSourceTask
        ) { RecursivelyFetchSourceTask it ->
            it.group = "Dependencies"
            it.description = "Retrieves only the first level 'sourceDependencies'."
        }
        Task beforeFetchSourceDependenciesTask = (DefaultTask)project.task(
            "beforeFetchSourceDependencies",
            type: DefaultTask
        ) { DefaultTask it ->
            it.group = "Dependencies"
            it.description = "Runs before source dependencies are fetched, to check authorisation setup. " +
                "Extend it with doLast if needed."
        }
        project.task("fixMercurialIni", type: DefaultTask) { Task it ->
            it.group = "Source Dependencies"
            it.description = "Modify/create your mercurial.ini file as required."
            it.doLast {
                Helper.fixMercurialIni()
            }
        }

        // Lazy configuration is a "secret" internal feature for use by plugins.  If a task adds a ".ext.lazyConfiguration"
        // property containing a single Closure, it will be executed just before that specific task runs.  As long as
        // intrepid-plugin is applied in a build script, this is available to all plugins.
        project.rootProject.gradle.taskGraph.beforeTask { Task task ->
            if (task.hasProperty("lazyConfiguration")) {
                Closure lazyConfig = task.lazyConfiguration
                if (lazyConfig != null) {
                    task.logger.info("Applying lazyConfiguration for task ${task.name}.")
                    profilingHelper.timing("${task.name}#!lazyConfiguration") {
                        task.configure lazyConfig
                    }
                    task.lazyConfiguration = null
                }
            }
        }

        /**************************************
         * Packaging and publishing stuff
         **************************************/
        
        // Define an 'everything' configuration which depends on all other configurations.
        Configuration everythingConf = configurations.findByName("everything") ?: configurations.add("everything")
        project.gradle.projectsEvaluated {
            configurations.each { Configuration conf ->
                if (conf.name != "everything" && !conf.name.startsWith("private")) {
                    everythingConf.extendsFrom conf
                }
            }
        }

        /**************************************
         * Source dependencies
         **************************************/        
        project.gradle.projectsEvaluated {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for tasks for sourceDependency versions") {
                // Do we have any Hg source dependencies? Need to check for Hg prerequisite, and fetch hg.exe.
                if (sourceDependencies.findAll{it.protocol == "hg"}.size() > 0) {
                    beforeFetchSourceDependenciesTask.doFirst {
                        prerequisites.check("HgAuth")
                    }
                    Task hgUnpackTask = buildScriptDependencies.getUnpackTask("Mercurial")
                    beforeFetchSourceDependenciesTask.dependsOn hgUnpackTask
                }

                Map<String, Task> buildTasks = Helper.getProjectBuildTasks(project)

                // For each source dependency, create a suitable task and link it into the
                // fetchAllDependencies task.
                sourceDependencies.each { sourceDep ->
                    if (sourceDep.usePublishedVersion) {
                        String depCoord = sourceDep.getDynamicPublishedDependencyCoordinate(project)
                        // println ":${project.name} - using published version of ${sourceDep.name} - ${depCoord}"
                        PackedDependencyHandler packedDep = new PackedDependencyHandler(
                            sourceDep.name,
                            project,
                            depCoord,
                            sourceDep.publishingHandler.configurations
                        )
                        packedDependencies.add(packedDep)
                    } else {
                        Task fetchTask = sourceDep.createFetchTask(project, buildScriptDependencies)
                        fetchTask.dependsOn beforeFetchSourceDependenciesTask
                        fetchAllSourceDependenciesTask.dependsOn fetchTask
                        fetchFirstLevelSourceDependenciesTask.dependsOn fetchTask

                        // Set up build task dependencies.
                        Project depProject = project.findProject(":${sourceDep.name}")
                        if (depProject != null) {
                            Map<String, Task> subBuildTasks = Helper.getProjectBuildTasks(depProject)
                            buildTasks.each { taskName, task ->
                                if (subBuildTasks.containsKey(taskName)) {
                                    task.dependsOn subBuildTasks[taskName]
                                }
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

                // Define the tasks for source-dependency projects
                Iterable<SourceDependencyHandler> sourceDeps = Helper.getTransitiveSourceDependencies(project)
                sourceDeps.each { sourceDep ->
                    Project sourceDepProj = sourceDep.getSourceDependencyProject(project)
                    if (sourceDepProj != null) {
                        sourceDependencyTasks.each { command ->
                            command.defineTask(sourceDepProj)
                        }
                    }
                }

                // Define any tasks for this project.
                sourceDependencyTasks.each { command ->
                    command.defineTask(project)
                }

                // Define the tasks dependencies for source-dependency projects
                sourceDeps.each { sourceDep ->
                    Project sourceDepProj = sourceDep.getSourceDependencyProject(project)
                    if (sourceDepProj != null) {
                        sourceDependencyTasks.each { command ->
                            command.configureTaskDependencies(sourceDepProj)
                        }
                    }
                }

                // Define task dependencies for this project.
                sourceDependencyTasks.each { command ->
                    command.configureTaskDependencies(project)
                }
            }
        }
         
        /**************************************
         * Create symlinks
         **************************************/
        project.gradle.projectsEvaluated {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for symlinks tasks") {
                symlinks.getMappings().each {
                    final File linkDir = new File(project.projectDir, it.linkPath)
                    final File targetDir = new File(project.projectDir, it.targetPath)
                    rebuildSymlinksTask.addLink(linkDir, targetDir)
                    deleteSymlinksTask.doLast {
                        Symlink.delete(linkDir)
                    }
                }
            }
        }
        
        /**************************************
         * Unpacking stuff
         **************************************/

        // This task will be initialized once the project is evaluated, because we need packed dependencies info.
        CollectDependenciesTask collectDependenciesTask = (CollectDependenciesTask)project.task(
            "collectDependencies",
            type: CollectDependenciesTask
        ) { CollectDependenciesTask task ->
            task.group = "Dependencies"
            task.description = "Collect all non-source dependencies into a 'local_artifacts' folder."
        }

        project.gradle.projectsEvaluated {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for populating symlink-to-cache tasks") {
                // Initialize for symlinks from workspace to the unpack cache, for each dependency which was unpacked to
                // the unpack cache (as opposed to unpacked directly to the workspace).
                //
                // Need to do this in projectsEvaluated so we can be sure that all packed dependencies have been set up.
                deleteSymlinksToCacheTask.addUnpackModuleVersions(packedDependenciesState)
                rebuildSymlinksToCacheTask.addUnpackModuleVersions(packedDependenciesState)
            }

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for cross-project symlink task dependencies") {
                // Make the symlink creation in this project depend on that in its source dependencies.  This makes it
                // possible to create symlinks (using the "symlinks" DSL) which point to symlinks in other projects.
                // Without this dependency, the target symlinks might not have been created when this project tries to
                // create symlinks to them.
                //
                // Need to do this in projectsEvaluated so we can be sure that all source dependencies have been set up.
                sourceDependenciesState.allConfigurationsPublishingSourceDependencies.each { Configuration conf ->
                    rebuildSymlinksTask.dependsOn conf.getTaskDependencyFromProjectDependency(true, rebuildSymlinksTask.name)
                    deleteSymlinksTask.dependsOn conf.getTaskDependencyFromProjectDependency(true, deleteSymlinksTask.name)
                }
            }

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for populating pathsForPackedDependencies") {
                unpackDependenciesTask.addUnpackModuleVersions(packedDependenciesState)
            }

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for createSettingsFileForPackedDependencies") {
                final PackedDependencyHandler packedDependenciesDefault = project.packedDependenciesDefault as PackedDependencyHandler
                if (project == project.rootProject && packedDependenciesDefault.shouldCreateSettingsFile()) {
                    Task t = project.task("createSettingsFileForPackedDependencies", type: DefaultTask) { Task task ->
                        task.doLast {
                            Collection<String> pathsForPackedDependencies = new ArrayList<String>()
                            packedDependenciesState.allUnpackModules.each { UnpackModule module ->
                                module.versions.values().each { UnpackModuleVersion versionInfo ->
                                    if (!versionInfo.hasArtifacts()) {
                                        // Nothing will have been unpacked/symlinked, so don't try to include it.
                                        logger.info("Not writing settings entry for empty packedDependency ${versionInfo.moduleVersion}")
                                        return
                                    }
                                    final File targetPathInWorkspace = versionInfo.getTargetPathInWorkspace(project)
                                    final relativePathInWorkspace =
                                        Helper.relativizePath(targetPathInWorkspace, project.rootProject.projectDir)
                                    pathsForPackedDependencies.add(relativePathInWorkspace)
                                }
                            }
                            pathsForPackedDependencies = pathsForPackedDependencies.unique()
                            SettingsFileHelper.writeSettingsFile(
                                new File(project.projectDir, "settings.gradle"),
                                pathsForPackedDependencies
                            )
                        }
                    }
                    fetchAllDependenciesTask.dependsOn t
                }
            }
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for checkPackedDependencies") {
                final PublishPackagesExtension publishPackages = project.publishPackages as PublishPackagesExtension
                publishPackages.defineCheckTask(packedDependenciesState)
            }

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for collectDependenciesTask") {
                /**************************************
                 * Collecting dependencies
                 **************************************/
                collectDependenciesTask.initialize(project)
            }

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for copyArtifacts") {
                /**************************************
                 * Copying artifacts
                 **************************************/
                copyArtifacts.defineCopyTask(project)
            }
        }

        timer.endBlock()
    }
}


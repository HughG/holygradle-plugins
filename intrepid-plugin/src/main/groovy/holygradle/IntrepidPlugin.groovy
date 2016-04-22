package holygradle

import holygradle.artifacts.*
import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.CustomGradleCorePlugin
import holygradle.custom_gradle.PrerequisitesChecker
import holygradle.custom_gradle.PrerequisitesExtension
import holygradle.custom_gradle.util.ProfilingHelper
import holygradle.custom_gradle.util.Symlink
import holygradle.dependencies.*
import holygradle.io.FileHelper
import holygradle.packaging.PackageArtifactHandler
import holygradle.publishing.DefaultPublishPackagesExtension
import holygradle.publishing.PublishPackagesExtension
import holygradle.scm.SourceControlRepositories
import holygradle.source_dependencies.RecursivelyFetchSourceTask
import holygradle.source_dependencies.SourceDependenciesStateHandler
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.source_dependencies.SourceDependencyTaskHandler
import holygradle.symlinks.SymlinkHandler
import holygradle.symlinks.SymlinkTask
import holygradle.symlinks.SymlinksToCacheTask
import holygradle.unpacking.GradleZipHelper
import holygradle.unpacking.PackedDependenciesStateHandler
import holygradle.unpacking.SevenZipHelper
import holygradle.unpacking.SpeedyUnpackManyTask
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.result.*
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution
import org.gradle.api.publish.PublishingExtension

public class IntrepidPlugin implements Plugin<Project> {
    public static final String EVERYTHING_CONFIGURATION_NAME = "everything"

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
         * Configurations and ConfigurationSets
         **************************************/
        // Mark configurations whose name begins with "private" as private in Ivy terms.
        project.configurations.whenObjectAdded((Closure){ Configuration conf ->
            if (conf.name.startsWith("private")) {
                conf.visible = false
            }
        })

        // Set the default version conflict behaviour to failure, unless explicitly overridden in the script, or unless
        // we're running one of the standard tasks used to resolve version conflicts.
        DependenciesSettingsHandler dependenciesSettings =
            DependenciesSettingsHandler.findOrCreateDependenciesSettings(project)
        final ConfigurationContainer configurations = project.configurations
        final List<String> baseTaskNames = project.gradle.startParameter.taskNames.collect { it.split(":").last() }
        final boolean runningDependencyTask = ["dependencies", "dependencyInsight"].any { baseTaskNames.contains(it) }
        // We call failOnVersionConflict inside projectsEvaluated so that the script can set
        // dependenciesSettings.defaultFailOnVersionConflict at any point, instead of having to be careful to call it
        // before any configurations are added, which would be a surprising constraint--at least, it surprised me,
        // coming back to this option after a couple of months.  We don't just use project.afterEvaluate because the
        // default involves falling back to the root project's setting.
        project.gradle.projectsEvaluated {
            configurations.all((Closure) { Configuration conf ->
                    if (dependenciesSettings.defaultFailOnVersionConflict && !runningDependencyTask) {
                        conf.resolutionStrategy.failOnVersionConflict()
                    }
                }
            )
        }

        project.extensions.configurationSetTypes = project.container(ConfigurationSetType) { String name ->
            new DefaultConfigurationSetType(name)
        }
        project.extensions.configurationSetTypes.addAll(DefaultVisualStudioConfigurationSetTypes.TYPES.values())
        project.extensions.configurationSetTypes.addAll(DefaultWebConfigurationSetTypes.TYPES.values())

        project.extensions.configurationSets = project.container(ConfigurationSet) { String name ->
            new ProjectConfigurationSet(name, project)
        }
  
        /**************************************
         * Properties
         **************************************/
        // The first properties are common to all projects, but the unpackModules collection is per-project.
        project.rootProject.ext.svnConfigPath = System.getenv("APPDATA") + "/Subversion"

        /**************************************
         * DSL extensions
         **************************************/

        // Define the 'packedDependenciesSettingsHandler' DSL for the project (used by BuildScriptDependencies).
        /*PackedDependenciesSettingsHandler packedDependencySettings =*/
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project)

        // Prepare dependencies that this plugin will require.
        BuildScriptDependencies buildScriptDependencies = BuildScriptDependencies.initialize(project)
        // Only define the build script dependencies for the root project because they're shared across all projects.
        if (project == project.rootProject) {
            // 7zip
            buildScriptDependencies.add("sevenZip", true)

            // Mercurial
            buildScriptDependencies.add("credential-store")
        }

        // Define the 'packedDependency' DSL for the project.
        Collection<PackedDependencyHandler> packedDependencies = PackedDependencyHandler.createContainer(project)

        // Define the 'sourceOverrides' DSL for the project
        /*Collection<SourceOverrideHandler> sourceOverrides =*/ SourceOverrideHandler.createContainer(project)

        // Define the 'dependenciesState' DSL for the project.
        /*DependenciesStateHandler dependenciesState =*/ DependenciesStateHandler.createExtension(project)

        // Define the 'dependenciesState' DSL for the project's build script.
        /*DependenciesStateHandler buildscriptDependenciesState =*/ DependenciesStateHandler.createExtension(project, true)

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

        /*Task fetchAllDependenciesTask =*/ project.task(
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
        /*SummariseAllDependenciesTask allDependenciesTask =*/ (SummariseAllDependenciesTask)project.task(
            "summariseAllDependencies",
            type: SummariseAllDependenciesTask
        ) { SummariseAllDependenciesTask it ->
            it.group = "Dependencies"
            it.description = "Create an XML file listing all direct and transitive dependencies"
            it.initialize()
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
        Configuration everythingConf =
            configurations.findByName(EVERYTHING_CONFIGURATION_NAME) ?: configurations.add(EVERYTHING_CONFIGURATION_NAME)
        project.gradle.projectsEvaluated {
            configurations.each((Closure){ Configuration conf ->
                if (conf.name != EVERYTHING_CONFIGURATION_NAME && conf.visible) {
                    everythingConf.extendsFrom conf
                }
            })
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
                }

                Map<String, Task> buildTasks = Helper.getProjectBuildTasks(project)

                // For each source dependency, create a suitable task and link it into the
                // fetchAllDependencies task.
                sourceDependencies.each { sourceDep ->
                    Task fetchTask = sourceDep.createFetchTask(project, buildScriptDependencies)
                    fetchTask.dependsOn beforeFetchSourceDependenciesTask
                    fetchAllSourceDependenciesTask.dependsOn fetchTask
                    fetchFirstLevelSourceDependenciesTask.dependsOn fetchTask

                    // Set up build task dependencies.
                    Project depProject = project.findProject(":${sourceDep.name}")
                    if (depProject != null) {
                        Map<String, Task> subBuildTasks = Helper.getProjectBuildTasks(depProject)
                        buildTasks.each { String taskName, Task task ->
                            if (subBuildTasks.containsKey(taskName)) {
                                task.dependsOn subBuildTasks[taskName]
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
                Iterable<SourceDependencyHandler> sourceDeps = Helper.getTransitiveSourceDependencies(project)
                sourceDeps.each { sourceDep ->
                    Project sourceDepProj = sourceDep.getSourceDependencyProject()
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

        if (project == project.rootProject) {
            CollectDependenciesTask collectDependenciesTask = (CollectDependenciesTask)project.task(
                "collectDependencies",
                type: CollectDependenciesTask
            ) { CollectDependenciesTask task ->
                task.group = "Dependencies"
                task.description = "Collect all non-source dependencies into a '${CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME}' folder."
            }
            profilingHelper.timing("IntrepidPlugin(${project}) configure collectDependenciesTask") {
                collectDependenciesTask.initialize()
            }
            ZipDependenciesTask zipDependenciesTask = (ZipDependenciesTask)project.task(
                "zipDependencies",
                type: ZipDependenciesTask
            ) { ZipDependenciesTask task ->
                task.group = "Dependencies"
                task.description = "Collect all non-source dependencies into a '${CollectDependenciesHelper.LOCAL_ARTIFACTS_DIR_NAME}' ZIP."
            }
            profilingHelper.timing("IntrepidPlugin(${project}) configure zipDependenciesTask") {
                zipDependenciesTask.initialize()
            }
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

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for populating pathsForPackedDependencies") {
                unpackDependenciesTask.addUnpackModuleVersions(packedDependenciesState)
            }

            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for checkPackedDependencies") {
                final PublishPackagesExtension publishPackages = project.publishPackages as PublishPackagesExtension
                publishPackages.defineCheckTask(packedDependenciesState)
            }
        }

        project.afterEvaluate {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for cross-project symlink task dependencies") {
                // Make the symlink creation in this project depend on that in its source dependencies.  This makes it
                // possible to create symlinks (using the "symlinks" DSL) which point to symlinks in other projects.
                // Without this dependency, the target symlinks might not have been created when this project tries to
                // create symlinks to them.
                //
                // Need to do this in project.afterEvaluate so we can be sure that all configurations and source
                // dependencies have been set up but we don't need to know about subprojects' source dependencies).
                sourceDependenciesState.allConfigurationsPublishingSourceDependencies.each { Configuration conf ->
                    rebuildSymlinksTask.dependsOn conf.getTaskDependencyFromProjectDependency(true, rebuildSymlinksTask.name)
                    deleteSymlinksTask.dependsOn conf.getTaskDependencyFromProjectDependency(true, deleteSymlinksTask.name)
                }
            }
        }

        setupSourceReplacementListener(project)



        timer.endBlock()
    }

    private void setupSourceReplacementListener(Project project) {
        Collection<PackedDependencyHandler> packedDependencies = project.packedDependencies
        NamedDomainObjectContainer<SourceOverrideHandler> sourceOverrides = project.sourceOverrides

        // Todo: throw an exception if source overrides are defined in a non-root project. It would be nice to actually
        // support this but we will have to walk back up the tree to do it

        // Todo: Force this to the start of the repo list
        // Consider checking whether there are any source overrides before adding this. This will need to be done on a
        // trigger on the container because there are no items in the list at this point.
        File tempDir = new File(
            project.buildDir,
            "holygradle/source_replacement"
        )
        FileHelper.ensureMkdirs(tempDir)

        project.repositories {
            ivy {
                url tempDir.toURI().toURL()
            }
        }

        def listener = new DependencyResolutionListener() {
            private Exception beforeResolveException = null

            void beforeResolve(ResolvableDependencies resolvableDependencies) {
                if (!resolvableDependencies.path.startsWith(':')) {
                    // This is a buildscript configuration, so there's nothing for us to do with source overrides.
                    return
                }
                project.logger.lifecycle("beforeResolve ${resolvableDependencies.path}")
                Configuration configuration = project.configurations.findByName(resolvableDependencies.name)
                if (configuration == null) {
                    return
                }

                // Add the version forcing bit to each configuration here
                sourceOverrides.each { SourceOverrideHandler handler ->
                    project.logger.lifecycle("beforeResolve ${resolvableDependencies.name}; handler for ${handler.dependencyCoordinate}")
                    try {
                        handler.createDummyModuleFiles()
                    } catch (Exception e) {
                        // Throwing in the beforeResolve handler leaves logging in a broken state so we need to catch
                        // this exception and throw it later.
                        beforeResolveException = e
                        // Now do an early exit from the closure, as if we had thrown.
                        return
                    }
                    // TODO 2016-02-26 HUGR: This "eachDependency" call should be the outer loop, outside the resolution
                    // handler altogether!
                    configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                        project.logger.lifecycle("beforeResolve ${resolvableDependencies.name}; handler for ${handler.dependencyCoordinate}; dep ${details.requested} target ${details.target}")
                        // TODO 2016-02-26 HUGR: If there's a dependency conflict, the 'requested' version may NOT be
                        // the one in the source override handler, even though the current 'target' version is.  I'm
                        // not sure if source overrides should always be compared against the target, or the requested
                        // and the target, or optionally (by default?) you specify a source override without a version,
                        // or what.
                        if (details.requested.group == handler.groupName &&
                            details.requested.name == handler.dependencyName &&
                            details.requested.version == handler.versionStr
                        ) {
                            project.logger.lifecycle("  MATCH requested: using ${handler.dummyVersionString}")
                            details.useVersion(handler.dummyVersionString)
                        }
                        if (details.target.group == handler.groupName &&
                            details.target.name == handler.dependencyName &&
                            details.target.version == handler.versionStr
                        ) {
                            project.logger.lifecycle("  MATCH target: using ${handler.dummyVersionString}")
                            details.useVersion(handler.dummyVersionString)
                        }
                        if (details.target.group == handler.groupName &&
                            details.target.name == handler.dependencyName &&
                            details.target.version.endsWith("+")
                        ) {
                            project.logger.warn("  MATCH FLOATING version: using ${handler.dummyVersionString}")
                            details.useVersion(handler.dummyVersionString)
                        }
                    }
                }
            }

            // Todo: Make this not run once per configuration
            void afterResolve(ResolvableDependencies resolvableDependencies) {
                if (!resolvableDependencies.path.startsWith(':')) {
                    // This is a buildscript configuration, so there's nothing for us to do with source overrides.
                    return
                }

                project.logger.lifecycle("source overrides: afterResolve ${resolvableDependencies.path})")
                if (beforeResolveException != null) {
                    // This exception is stored from earlier and thrown here.  Usually this is too deeply nested for
                    // Gradle to output useful information at the default logging level, so we also explicitly log the
                    // the nested exception.
                    final String message = "beforeResolve handler failed"
                    project.logger.error("${message}: ${beforeResolveException.toString()}")
                    throw new RuntimeException(message, beforeResolveException)
                }

                resolvableDependencies.resolutionResult.allDependencies.each { DependencyResult result ->
                    if (result instanceof ResolvedDependencyResult) {
                        project.logger.lifecycle("  resolved ${result.requested} to ${((ResolvedDependencyResult)result).selected}")
                    } else {
                        project.logger.lifecycle("  UNRESOLVED ${result.requested}: ${(UnresolvedDependencyResult)result}")
                    }
                }

                // Todo: This should be able to be pre-calculated but Groovy's closure capture rules are making it harder than it should be
                def sourceOverrideCache = new HashMap<SourceOverrideHandler, HashMap<String, HashMap<String, String>>>()

                // Pre-generate dependency files for each source override
                sourceOverrides.each { SourceOverrideHandler handler ->
                    handler.generateDependencyFiles()
                }

                // Pre-calculate the dependencies so we only have to loop once
                sourceOverrides.each { SourceOverrideHandler handler ->
                    File dependencyFile = handler.dependenciesFile
                    def dependencyXml = new XmlSlurper(false, false).parse(dependencyFile)

                    // Build hashsets from the dependency XML
                    def remoteConfigurations = new HashMap<String, HashMap<String, String>>()
                    dependencyXml.Configuration.each { config ->
                        def remoteDependencies = new HashMap<String, String>()

                        config.Dependency.each { dep ->
                            def calculatedVersion = dep.@isSource.toBoolean() ? Helper.convertPathToVersion(dep.@absolutePath.toString()) : dep.@version.toString()
                            remoteDependencies.put("${dep.@group.toString()}:${dep.@name.toString()}", calculatedVersion)
                        }

                        remoteConfigurations.put(config.@name, remoteDependencies)
                    }

                    sourceOverrideCache.put(handler, remoteConfigurations)
                }

                sourceOverrides.each { SourceOverrideHandler handler ->
                    // Compare source override full dependencies to the current dependency
                    def dependencyCache = sourceOverrideCache.get(handler)

                    List<String> mismatchMessages = new LinkedList<String>()
                    resolvableDependencies.resolutionResult.allModuleVersions { ResolvedModuleVersionResult module ->
                        dependencyCache.each { configName, config ->
                            def moduleKey = "${module.id.group.toString()}:${module.id.name.toString()}"
                            if (config.containsKey(moduleKey)) {
                                if (config.get(moduleKey) != module.id.version.toString()) {
                                    final String message = "Module '${module.id}' does not match the dependency " +
                                        "declared in source override '${handler.name}' " +
                                        "(${moduleKey}:${config.get(moduleKey)}); " +
                                        "you may have to declare a matching source override in the source project."
                                    mismatchMessages << message
                                }
                            }
                        }
                    }

                    // Compare source override full dependencies to each other
                    sourceOverrides.each { SourceOverrideHandler handler2 ->
                        def dependencyCache2 = sourceOverrideCache.get(handler2)

                        dependencyCache.each { configName, config ->
                            dependencyCache2.each { configName2, config2 ->
                                config.each { dep, version ->
                                    if (config2.containsKey(dep) && config2.get(dep) != version) {
                                        final String message = "Module in source override '${handler.name}' " +
                                            "(${dep}:${version}) does not match module in source override " +
                                            "'${handler2.name}' (${dep}:${config2.get(dep)}); " +
                                            "you may have to declare a matching source override."
                                        mismatchMessages << message
                                    }
                                }
                            }
                        }
                    }

                    if (!mismatchMessages.empty) {
                        StringWriter stringWriter = new StringWriter()
                        stringWriter.withPrintWriter { pw ->
                            pw.println(
                                "The module version for one or more source overrides does not match the version " +
                                "for the same module in one or more places; please check the following messages."
                            )
                            for (message in mismatchMessages) {
                                pw.print("    ")
                                pw.println(message)
                            }
                        }
                        String message = stringWriter.toString()

                        final Configuration configuration = project.configurations.getByName(resolvableDependencies.name)
                        final ResolutionStrategy strategy = configuration.resolutionStrategy
                        final boolean hasStrictResolutionStrategy =
                            (strategy instanceof ResolutionStrategyInternal) &&
                                ((ResolutionStrategyInternal)strategy).conflictResolution instanceof StrictConflictResolution

                        if (hasStrictResolutionStrategy) {
                            throw new RuntimeException(message)
                        } else {
                            project.logger.warn(message)
                        }
                    }
                }
            }
        }

        project.gradle.addListener(listener)
    }
}
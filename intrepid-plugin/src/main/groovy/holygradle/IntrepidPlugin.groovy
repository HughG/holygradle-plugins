package holygradle

import holygradle.artifacts.*
import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.CustomGradleCorePlugin
import holygradle.custom_gradle.PrerequisitesChecker
import holygradle.custom_gradle.PrerequisitesExtension
import holygradle.custom_gradle.util.ProfilingHelper
import holygradle.dependencies.*
import holygradle.io.Link
import holygradle.io.FileHelper
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
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.result.*
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution
import org.gradle.api.publish.PublishingExtension

public class IntrepidPlugin implements Plugin<Project> {
    public static final String EVERYTHING_CONFIGURATION_NAME = "everything"
    public static final String EVERYTHING_CONFIGURATION_PROPERTY = "createEverythingConfiguration"

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
        
        // Define 'links' DSL block (and deprecated 'symlinks' one).
        LinkHandler links = LinkHandler.createExtension(project)
        
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

        LinksToCacheTask deleteLinksToCacheTask = (LinksToCacheTask)project.task(
            "deleteLinksToCache", type: LinksToCacheTask
        ) { Task it ->
            it.group = "Dependencies"
            it.description = "Delete all links to the unpack cache"
        }
        deleteLinksToCacheTask.initialize(LinksToCacheTask.Mode.CLEAN)
        project.task("deleteSymlinksToCache") { Task t ->
            t.dependsOn deleteLinksToCacheTask
            t.description = "${t.name} is deprecated and will be removed in future.  Use deleteLinksToCache instead"
            t.doFirst { logger.warn(t.description) }
        }
        LinksToCacheTask rebuildLinksToCacheTask = (LinksToCacheTask)project.task(
            "rebuildLinksToCache", type: LinksToCacheTask
        ) { Task it ->
            it.group = "Dependencies"
            it.description = "Rebuild all links to the unpack cache"
            // We need to make sure we unpack before trying to create a link to the cache, or we will end up failing to
            // create directory junctions, or with broken file symlinks instead of working directory symlinks.
            it.dependsOn unpackDependenciesTask
        }
        rebuildLinksToCacheTask.initialize(LinksToCacheTask.Mode.BUILD)
        project.task("rebuildSymlinksToCache") { Task t ->
            t.dependsOn rebuildLinksToCacheTask
            t.description = "${t.name} is deprecated and will be removed in future.  Use rebuildLinksToCache instead"
            t.doFirst { logger.warn(t.description) }
        }

        final FETCH_ALL_DEPENDENCIES_TASK_NAME = "fetchAllDependencies"
        RecursivelyFetchSourceTask fetchAllSourceDependenciesTask = (RecursivelyFetchSourceTask)project.task(
            "fetchAllSourceDependencies",
            type: RecursivelyFetchSourceTask
        ) { RecursivelyFetchSourceTask it ->
            it.group = "Dependencies"
            it.description = "Retrieves all 'sourceDependencies' recursively."
            it.recursiveTaskName = FETCH_ALL_DEPENDENCIES_TASK_NAME
        }

        Task deleteLinksTask = project.task("deleteLinks", type: DefaultTask) { Task it ->
            it.group = "Dependencies"
            it.description = "Remove all links."
            it.dependsOn deleteLinksToCacheTask
        }
        project.task("deleteSymlinks") { Task t ->
            t.dependsOn deleteLinksTask
            t.description = "${t.name} is deprecated and will be removed in future.  Use deleteLinks instead"
            t.doFirst { logger.warn(t.description) }
        }
        LinkTask rebuildLinksTask = (LinkTask)project.task("rebuildLinks", type: LinkTask) { Task it ->
            it.group = "Dependencies"
            it.description = "Rebuild all links."
            it.dependsOn rebuildLinksToCacheTask
            // Some links might be to other source folders, so make sure we fetch them first.
            it.dependsOn fetchAllSourceDependenciesTask
        }
        rebuildLinksTask.initialize()
        project.task("rebuildSymlinks") { Task t ->
            t.dependsOn rebuildLinksTask
            t.description = "${t.name} is deprecated and will be removed in future.  Use rebuildLinks instead"
            t.doFirst { logger.warn(t.description) }
        }

        /*Task fetchAllDependenciesTask =*/ project.task(
            FETCH_ALL_DEPENDENCIES_TASK_NAME,
            type: DefaultTask
        ) { Task it ->
            it.group = "Dependencies"
            it.description = "Retrieves all 'packedDependencies' and 'sourceDependencies', and sets up necessary links."
            it.dependsOn rebuildLinksTask
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

        if (project.hasProperty(EVERYTHING_CONFIGURATION_PROPERTY)) {
            // Define an 'everything' configuration which depends on all other configurations.
            Configuration everythingConf =
                    configurations.findByName(EVERYTHING_CONFIGURATION_NAME) ?: configurations.add(EVERYTHING_CONFIGURATION_NAME)
            project.gradle.projectsEvaluated {
                configurations.each((Closure) { Configuration conf ->
                    if (conf.name != EVERYTHING_CONFIGURATION_NAME && conf.visible) {
                        everythingConf.extendsFrom conf
                    }
                })
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
         * Create links
         **************************************/
        project.gradle.projectsEvaluated {
            profilingHelper.timing("IntrepidPlugin(${project})#projectsEvaluated for links tasks") {
                links.getMappings().each {
                    final File linkDir = new File(project.projectDir, it.linkPath)
                    final File targetDir = new File(project.projectDir, it.targetPath)
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
                final PublishPackagesExtension publishPackages = project.publishPackages as PublishPackagesExtension
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
                sourceDependenciesState.allConfigurationsPublishingSourceDependencies.each { Configuration conf ->
                    rebuildLinksTask.dependsOn conf.getTaskDependencyFromProjectDependency(true, rebuildLinksTask.name)
                    deleteLinksTask.dependsOn conf.getTaskDependencyFromProjectDependency(true, deleteLinksTask.name)
                }
            }
        }

        setupSourceReplacementListener(project)



        timer.endBlock()
    }

    private void setupSourceReplacementListener(Project project) {
        NamedDomainObjectContainer<SourceOverrideHandler> sourceOverrides = project.sourceOverrides

        // Fail if source overrides are added in a non-root project, because it will be even more effort to tie together
        // overrides across subprojects and cross-check them.  We'd like to support that some day, maybe.  We could just
        // not add the DSL handler to non-root projects, but then the user would only get a MissingMethodException
        // instead of a useful error message.
        sourceOverrides.whenObjectAdded {
            if (project != project.rootProject) {
                throw new RuntimeException("Currently sourceOverrides can only be used in the root project.")
            }
        }

        // If and when any source overrides are added, we also need to add a repo to contain dummy entries for all the
        // overridden module versions.  We force this to the start of the project repo list because it will contain
        // relatively few entries, and those versions won't appear in any normal repo.
        IvyArtifactRepository sourceOverrideDummyModulesRepo = null
        sourceOverrides.whenObjectAdded {
            if (sourceOverrideDummyModulesRepo == null) {
                File tempDir = new File(project.buildDir, "holygradle/source_replacement")
                FileHelper.ensureMkdirs(tempDir)
                sourceOverrideDummyModulesRepo = project.repositories.ivy { it.url = tempDir.toURI() }
                project.repositories.remove(sourceOverrideDummyModulesRepo)
                project.repositories.addFirst(sourceOverrideDummyModulesRepo)
            }
        }

        // Add a dependency resolve rule for all configurations, which applies source overrides (if there are any).  We
        // use ConfigurationContainer#all so that this is applied for each configuration when it's created.
        project.configurations.all((Closure){ Configuration configuration ->
            // Gradle automatically converts Closures to Action<>, so ignore IntelliJ warning.
            //noinspection GroovyAssignabilityCheck
            configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                project.logger.debug(
                    "Checking for source replacements: requested ${details.requested}, target ${details.target}, " +
                    "in ${configuration}"
                )
                for (SourceOverrideHandler handler in sourceOverrides) {
                    if (shouldUseSourceOverride(project, details, handler)) {
                        details.useVersion(handler.dummyVersionString)
                        break
                    }
                }
            }
        })

        def listener = new DependencyResolutionListener() {
            private Exception beforeResolveException = null
            // Mapping each handler
            //   to each configuration in the replacement project
            //     to each "group:name" dependency in that configuration (including all transitive deps, not just
            // direct)
            //       to the "version" for that dependency (which might be a "normal" version or also a replacement).
            //
            // We use LinkedHashMaps to get a stable order for which things appear first in the error messages.
            private LinkedHashMap<
                SourceOverrideHandler, LinkedHashMap<String, LinkedHashMap<String,String>>
            > sourceOverrideDependencies = null

            // TODO 2016-04-29 HUGR: We should extend source overrides to allow different overrides per configuration,
            // but currently we don't.  So, we only need to calculate this once, which is helpful for the common case
            // where there are no conflicts.  (Even if we did it per configuration, we might be able to be smart about
            // not re-doing work if we considered extendsFrom relationships.)  In that case, we probably need to support
            // adding overrides in subprojects, because they might need to apply to configurations which are not
            // connected to any in the root project.
            private List<String> conflictBetweenOverrideMessages = null

            void beforeResolve(ResolvableDependencies resolvableDependencies) {
                if (findProjectConfiguration(resolvableDependencies) == null) {
                    project.logger.debug("source overrides: beforeResolve SKIPPING ${resolvableDependencies.path}")
                    return // because source overrides aren't applicable to the buildscript or to detached configurations
                }
                project.logger.debug("source overrides: beforeResolve ${resolvableDependencies.path}")

                // Create dummy module files for each source override.  (Internally it will only do this once per build
                // run.)  We do this in beforeResolve handler because we don't need to do it unless we are actually
                // resolving some configurations: for tasks like "tasks" we don't need to.
                sourceOverrides.each { SourceOverrideHandler handler ->
                    project.logger.debug(
                        "beforeResolve ${resolvableDependencies.name}; " +
                        "generating dummy module files for ${handler.dependencyCoordinate}"
                    )
                    try {
                        handler.generateDummyModuleFiles()
                    } catch (Exception e) {
                        // Throwing in the beforeResolve handler leaves logging in a broken state so we need to catch
                        // this exception and throw it later.
                        beforeResolveException = e
                    }
                }
            }

            void afterResolve(ResolvableDependencies resolvableDependencies) {
                final Configuration projectConfiguration = findProjectConfiguration(resolvableDependencies)
                if (projectConfiguration == null) {
                    project.logger.debug("source overrides: afterResolve SKIPPING ${resolvableDependencies.path}")
                    return // because source overrides aren't applicable to the buildscript or to detached configurations
                }

                project.logger.debug("source overrides: afterResolve ${resolvableDependencies.path}")
                rethrowIfBeforeResolveHadException()
                debugLogResolvedDependencies(resolvableDependencies)
                Map<SourceOverrideHandler, Map<String, Map<String, String>>> sourceOverrideDependencies =
                    getSourceOverrideDependencies()

                List<String> conflictMessages = new LinkedList<String>()

                sourceOverrideDependencies.each { handler, deps ->
                    // Compare source override full dependencies to the current dependency
                    collectConflictsForProjectVsOverride(conflictMessages, resolvableDependencies, handler, deps)
                }

                // Compare source override full dependencies to each other
                collectConflictsBetweenOverrides(conflictMessages, sourceOverrideDependencies)

                logOrThrowIfConflicts(conflictMessages, projectConfiguration)
            }

            private Configuration findProjectConfiguration(ResolvableDependencies resolvableDependencies) {
                final String depsPath = resolvableDependencies.path
                if (depsPath.startsWith(':')) {
                    return project.configurations.findByName(resolvableDependencies.name)
                } else {
                    // This is a buildscript configuration, or maybe a copy or detached configuration.
                    return null
                }
            }

            private void rethrowIfBeforeResolveHadException() {
                if (beforeResolveException != null) {
                    // This exception is stored from earlier and thrown here.  Usually this is too deeply nested for
                    // Gradle to output useful information at the default logging level, so we also explicitly log the
                    // the nested exception.
                    final String message = "beforeResolve handler failed"
                    project.logger.error("${message}: ${beforeResolveException.toString()}")
                    throw new RuntimeException(message, beforeResolveException)
                }
            }

            private void debugLogResolvedDependencies(ResolvableDependencies resolvableDependencies) {
                if (project.logger.isDebugEnabled()) {
                    resolvableDependencies.resolutionResult.allDependencies.each { DependencyResult result ->
                        if (result instanceof ResolvedDependencyResult) {
                            project.logger.debug(
                                "  resolved ${result.requested} to ${((ResolvedDependencyResult) result).selected}"
                            )
                        } else {
                            project.logger.debug(
                                "  UNRESOLVED ${result.requested}: ${(UnresolvedDependencyResult) result}"
                            )
                        }
                    }
                }
            }

            private Map<SourceOverrideHandler, Map<String, Map<String, String>>> getSourceOverrideDependencies() {
                if (sourceOverrideDependencies == null) {
                    readSourceOverrideDependencies()
                }
                return sourceOverrideDependencies
            }

            private void readSourceOverrideDependencies() {
                sourceOverrideDependencies =
                    new LinkedHashMap<SourceOverrideHandler, LinkedHashMap<String, LinkedHashMap<String,String>>>()

                sourceOverrides.each { SourceOverrideHandler handler ->
                    File dependencyFile = handler.dependenciesFile
                    def dependencyXml = new XmlSlurper(false, false).parse(dependencyFile)

                    // Build hashsets from the dependency XML
                    sourceOverrideDependencies[handler] =
                        dependencyXml.Configuration.list().collectEntries(new LinkedHashMap<>()) { config -> [
                            config.@name.toString(),
                            config.Dependency.list().collectEntries(new LinkedHashMap<>()) { dep -> [
                                "${dep.@group.toString()}:${dep.@name.toString()}",
                                (
                                    dep.@isSource.toBoolean()
                                        ? Helper.convertPathToVersion(dep.@absolutePath.toString())
                                        : dep.@version.toString()
                                )
                            ]}
                        ]} as LinkedHashMap<String, LinkedHashMap<String, String>>
                }
            }

            private collectConflictsForProjectVsOverride(
                List<String> conflictMessages,
                ResolvableDependencies resolvableDependencies,
                SourceOverrideHandler handler,
                Map<String, Map<String, String>> deps
            ) {
                resolvableDependencies.resolutionResult.allModuleVersions { ResolvedModuleVersionResult module ->
                    deps.each { configName, config ->
                        def moduleKey = "${module.id.group.toString()}:${module.id.name.toString()}"
                        if (config.containsKey(moduleKey)) {
                            if (config.get(moduleKey) != module.id.version.toString()) {
                                final String message = "For ${project} configuration ${resolvableDependencies.path}, " +
                                    "module '${module.id}' does not match the dependency " +
                                    "declared in source override '${handler.name}' configuration ${configName} " +
                                    "(${moduleKey}:${config.get(moduleKey)}); " +
                                    "you may have to declare a matching source override in the source project."
                                conflictMessages << message
                            }
                        }
                    }
                }
            }

            private void collectConflictsBetweenOverrides(
                List<String> conflictMessages,
                Map<SourceOverrideHandler, Map<String, Map<String, String>>> sourceOverrideDependencies
            ) {
                conflictMessages.addAll(getConflictsBetweenOverrides(sourceOverrideDependencies))
            }

            private List<String> getConflictsBetweenOverrides(
                Map<SourceOverrideHandler, Map<String, Map<String, String>>> sourceOverrideDependencies
            ) {
                if (conflictBetweenOverrideMessages == null) {
                    conflictBetweenOverrideMessages = new LinkedList<>()
                    calculateConflictsBetweenOverrides(conflictBetweenOverrideMessages, sourceOverrideDependencies)
                }
                return conflictBetweenOverrideMessages
            }

            private void calculateConflictsBetweenOverrides(
                List<String> conflictMessages,
                Map<SourceOverrideHandler, Map<String, Map<String, String>>> sourceOverrideDependencies
            ) {
                // The conflict check between different overrides is symmetrical, so we don't need to do it N^2 times,
                // only half that.
                Map<SourceOverrideHandler, Set<SourceOverrideHandler>> alreadyCompared =
                    new HashMap<>().withDefault { new HashSet<>() }
                sourceOverrideDependencies.each {
                    SourceOverrideHandler handler,
                    Map<String, Map<String, String>> deps
                    ->
                    sourceOverrideDependencies.each {
                        SourceOverrideHandler handler2,
                        Map<String, Map<String, String>> deps2
                            ->
                            if (handler == handler2 || alreadyCompared[handler].contains(handler2)) {
                                return
                            }
                            deps.each { configName, config ->
                                deps2.each { configName2, config2 ->
                                    config.each { dep, version ->
                                        if (config2.containsKey(dep) && config2.get(dep) != version) {
                                            final String message = "For ${project}, module in " +
                                                "source override '${handler.name}' configuration ${configName} " +
                                                "(${dep}:${version}) " +
                                                "does not match module in " +
                                                "source override '${handler2.name}' configuration ${configName} " +
                                                "(${dep}:${config2.get(dep)}); " +
                                                "you may have to declare a matching source override."
                                            conflictMessages << message
                                        }
                                    }
                                }
                            }
                            alreadyCompared[handler].add(handler2)
                            alreadyCompared[handler2].add(handler)
                    }
                }
            }

            private void logOrThrowIfConflicts(
                List<String> conflictMessages,
                Configuration configuration
            ) {
                if (!conflictMessages.empty) {
                    conflictMessages = conflictMessages.sort().unique()

                    StringWriter stringWriter = new StringWriter()
                    stringWriter.withPrintWriter { pw ->
                        pw.println(
                            "The module version for one or more source overrides does not match the version " +
                            "for the same module in one or more places; please check the following messages."
                        )
                        for (message in conflictMessages) {
                            pw.print("    ")
                            pw.println(message)
                        }
                    }
                    String message = stringWriter.toString()

                    if (hasStrictResolutionStrategy(configuration)) {
                        throw new RuntimeException(message)
                    } else {
                        project.logger.warn(message)
                    }
                }
            }

            private boolean hasStrictResolutionStrategy(Configuration configuration) {
                final ResolutionStrategy strategy = configuration.resolutionStrategy
                final boolean hasStrictResolutionStrategy =
                    (strategy instanceof ResolutionStrategyInternal) &&
                        ((ResolutionStrategyInternal) strategy).conflictResolution instanceof
                            StrictConflictResolution
                return hasStrictResolutionStrategy
            }
        }

        project.gradle.addListener(listener)
    }

    private static boolean shouldUseSourceOverride(
        Project project,
        DependencyResolveDetails details,
        SourceOverrideHandler handler
    ) {
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
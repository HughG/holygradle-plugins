package holygradle

import bsh.This
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
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
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
                    Task fetchTask = sourceDep.createFetchTask(project)
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

        profilingHelper.timing("IntrepidPlugin(${project}) set up source overrides") {
            setupSourceOverrides(project)
        }

        timer.endBlock()
    }

    private void setupSourceOverrides(Project project) {
        NamedDomainObjectContainer<SourceOverrideHandler> sourceOverrides = project.sourceOverrides
        IvyArtifactRepository sourceOverrideDummyModulesRepo = null

        sourceOverrides.whenObjectAdded {
            // Fail if source overrides are added in a non-root project, because it will be even more effort to tie together
            // overrides across subprojects and cross-check them.  We'd like to support that some day, maybe.  We could just
            // not add the DSL handler to non-root projects, but then the user would only get a MissingMethodException
            // instead of a useful error message.
            if (project != project.rootProject) {
                throw new RuntimeException("Currently sourceOverrides can only be used in the root project.")
            }

            // If and when any source overrides are added, we also need to add a repo to contain dummy entries for all the
            // overridden module versions.  We force this to the start of the project repo list because it will contain
            // relatively few entries, and those versions won't appear in any normal repo.
            if (sourceOverrideDummyModulesRepo == null) {
                File tempDir = new File(project.buildDir, "holygradle/source_override")
                FileHelper.ensureMkdirs(tempDir)
                sourceOverrideDummyModulesRepo = project.repositories.ivy { it.url = tempDir.toURI() }
                project.repositories.remove(sourceOverrideDummyModulesRepo)
                project.repositories.addFirst(sourceOverrideDummyModulesRepo)
            }
        }

        if (project == project.rootProject) {
            // Add a dependency resolve rule for all configurations, which applies source overrides (if there are any).  We
            // use ConfigurationContainer#all so that this is applied for each configuration when it's created.
            project.configurations.all((Closure){ Configuration configuration ->
                // Gradle automatically converts Closures to Action<>, so ignore IntelliJ warning.
                //noinspection GroovyAssignabilityCheck
                configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                    project.logger.debug(
                        "Checking for source overrides: requested ${details.requested}, target ${details.target}, " +
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

            project.gradle.addListener(new SourceOverridesDependencyResolutionListener(project))

            project.gradle.taskGraph.whenReady {
                // Do this late, because the 'dependencies' task won't have been created when we're initialising.
                addInformationToDependenciesTask(project)
            }
        }
    }

    private void addInformationToDependenciesTask(Project project) {
        project.tasks["dependencies"].doLast {
            if (!project.sourceOverrides.empty) {
//2345678901234567890123456789012345678901234567890123456789012345678901234567890 <-- 80-column ruler
                project.logger.lifecycle(
                    """
This project has source overrides, which have automatically-generated versions:
"""
                )
                for (SourceOverrideHandler override in project.sourceOverrides) {
                    project.logger.lifecycle(
                        "  ${override.dependencyCoordinate} -> ${override.dummyDependencyCoordinate}"
                    )
                    project.logger.lifecycle("    at ${override.from}")
                }
                project.logger.lifecycle(
                    """
The source override mechanism copies dependency information from override
projects and adds them as direct dependencies of the overridden module version
in the root project.  So, if you see version conflicts in the dependency trees
("group:name:version1 -> version2"), some of the conflicting versions may come
from the source override projects.

There are four possible causes of version conflicts involving source overrides.

1. The conflict is not related to source overrides and is just caused by normal
conflicts between versions of indirect dependencies.  You can resolve these as
normal.

2. The root build (the root project and/or its subprojects) has versions which
conflict with one or more source override builds (the root project of a source
override and/or its subprojects).  You can detect this because one of the
conflicting versions will appear in the tree under a module with an override
version ("SOURCE_..."), and the other will appear not under any override.  
You can resolve this by changing the dependency version in the root build, or
in the override build.  In the latter case it may be helpful to run the
'dependencies' task separately in each conflicting source override directory.

3. Similar to the previous case, two or more different source overrides
builds (the root project of a source override and/or its subprojects) may have
versions which conflict with each other, even though they don't conflict with
dependencies from the root project.  You can detect this because each
conflicting version will appear under a module with an override version
("SOURCE_...").  Again, to resolve this, change the dependency version in one
or more override builds.  It may be useful to run the 'dependencies' task
separately in each source override directory.  

4. Two or more source overrides may have been added for the same original
module version ("group:name:version") but pointing to different locations on
disk.  All overrides for the same original version must point to the same
location.  (They may be specified as different relative paths but they must
resolve to the same absolute location.)  You can detect this because the two
versions involved in the conflict are both source override versions
("SOURCE_...").  To resolve this, change all overrides for the same original
module version to point to the same location.

Remember that override projects can also contain further source overrides, so
you may have to run the 'dependencies' task at multiple levels.
"""
//2345678901234567890123456789012345678901234567890123456789012345678901234567890 <-- 80-column ruler
                )
            }
        }
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
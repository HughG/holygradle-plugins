package holygradle

import holygradle.buildscript.BuildScriptDependencies
import holygradle.custom_gradle.CustomGradleCorePlugin
import holygradle.custom_gradle.PrerequisitesChecker
import holygradle.custom_gradle.PrerequisitesExtension
import holygradle.dependencies.PackedDependencyHandler
import holygradle.packaging.PackageArtifactHandler
import holygradle.publishing.DefaultPublishPackagesExtension
import holygradle.publishing.PublishPackagesExtension
import holygradle.scm.SourceControlRepositories
import holygradle.source_dependencies.CopyArtifactsHandler
import holygradle.source_dependencies.RecursivelyFetchSourceTask
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.source_dependencies.SourceDependencyTaskHandler
import holygradle.symlinks.SymlinkHandler
import holygradle.symlinks.SymlinkTask
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.publish.PublishingExtension
import holygradle.custom_gradle.util.Symlink
import holygradle.unpacking.UnpackModule
import holygradle.dependencies.CollectDependenciesTask

public class IntrepidPlugin implements Plugin<Project> {
    
    void apply(Project project) {
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
        final ConfigurationContainer configurations = project.configurations
        configurations.all { Configuration it ->
            it.resolutionStrategy.failOnVersionConflict()
        }
  
        /**************************************
         * Properties
         **************************************/
        project.ext.svnConfigPath = System.getenv("APPDATA") + "/Subversion"
        project.ext.hgConfigFile = System.getenv("USERPROFILE") + "/mercurial.ini"
        project.ext.unpackedDependenciesCache = new File(project.gradle.gradleUserHomeDir, "unpackCache")
        project.ext.unpackModules = null
        
        /**************************************
         * Tasks
         **************************************/
        Task deleteSymlinksTask = project.task("deleteSymlinks", type: DefaultTask) { Task it ->
            it.group = "Dependencies"
            it.description = "Remove all symlinks."
        }
        SymlinkTask rebuildSymlinksTask = (SymlinkTask)project.task("rebuildSymlinks", type: SymlinkTask) { Task it ->
            it.group = "Dependencies"
            it.description = "Rebuild all symlinks."
        }
        RecursivelyFetchSourceTask fetchAllDependenciesTask = (RecursivelyFetchSourceTask)project.task(
            "fetchAllDependencies",
            type: RecursivelyFetchSourceTask
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
            it.recursive = false
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
                    task.configure lazyConfig
                    task.lazyConfiguration = null
                }
            }
        }

        /**************************************
         * DSL extensions
         **************************************/
        // Define the 'packedDependency' DSL for the build script.
        Collection<PackedDependencyHandler> packedDependencies = PackedDependencyHandler.createContainer(project)
        
        // DSL extension to specify source dependencies
        Collection<SourceDependencyHandler> sourceDependencies = SourceDependencyHandler.createContainer(project)
        
        // Define 'publishPackages' DSL block.
        PublishingExtension publishingExtension = project.extensions.getByType(PublishingExtension)
        project.extensions.create(
            "publishPackages", DefaultPublishPackagesExtension, project, publishingExtension, sourceDependencies, packedDependencies
        )
        
        // Define 'sourceControl' DSL.
        SourceControlRepositories.createExtension(project)
        
        // Define 'symlinks' DSL block.
        SymlinkHandler symlinks = SymlinkHandler.createExtension(project)
        
        // Define 'packageArtifacts' DSL for the build script.
        PackageArtifactHandler.createContainer(project)
        
        // Define 'copyArtifacts' DSL
        CopyArtifactsHandler copyArtifacts = CopyArtifactsHandler.createExtension(project)
        
        // Define 'sourceDependencyTasks' DSL
        Collection<SourceDependencyTaskHandler> sourceDependencyTasks = SourceDependencyTaskHandler.createContainer(project)
    
        // Prepare dependencies that this plugin will require.
        BuildScriptDependencies buildScriptDependencies = BuildScriptDependencies.initialize(project)
        
        // Only define the build script dependencies for the root project because they're shared
        // accross all projects.
        if (project == project.rootProject) {
            // 7zip
            buildScriptDependencies.add("sevenZip", true)
            fetchAllDependenciesTask.dependsOn buildScriptDependencies.getUnpackTask("sevenZip")
            
            // Mercurial
            buildScriptDependencies.add("Mercurial", true)
            buildScriptDependencies.add("credential-store")
            if (buildScriptDependencies.getPath("Mercurial") != null) {
                Task hgUnpackTask = buildScriptDependencies.getUnpackTask("Mercurial")
                hgUnpackTask.doLast {
                    Helper.addMercurialKeyringToIniFile(buildScriptDependencies.getPath("Mercurial"))
                }
                fetchAllDependenciesTask.dependsOn hgUnpackTask
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
            // Do we have any Hg source dependencies? Need to check for Hg prerequisite.
            if (sourceDependencies.findAll{it.protocol == "hg"}.size() > 0) {
                beforeFetchSourceDependenciesTask.doFirst {
                    prerequisites.check("HgAuth")
                }
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
                    fetchAllDependenciesTask.dependsOn fetchTask
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
        
        /**************************************
         * Source dependency commands
         **************************************/
        project.gradle.projectsEvaluated {
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
         
        /**************************************
         * Create symlinks
         **************************************/
        project.gradle.projectsEvaluated {
            symlinks.getMappings().each {
                final File linkDir = new File(project.projectDir, it.linkPath)
                final File targetDir = new File(project.projectDir, it.targetPath)
                rebuildSymlinksTask.configure(project, linkDir, targetDir)
                deleteSymlinksTask.doLast {
                    Symlink.delete(linkDir)
                }
            }
        }
        
        /**************************************
         * Unpacking stuff
         **************************************/
        
        // One 'unpack' task per 'packedDependency' block of DSL in the build script.
        project.gradle.projectsEvaluated {

            // For each artifact that is listed as a dependency, determine if we need to unpack it.
            Iterable<UnpackModule> unpackModules = UnpackModule.getAllUnpackModules(project)
            
            Collection<String> pathsForPackedDependencies = new ArrayList<String>()
            
            // Construct tasks to unpack the artifacts.
            unpackModules.each { module ->                
                module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                    // Get the unpack task which will unpack the module to the cache or directly to the workspace.
                    Task unpackTask = versionInfo.getUnpackTask(project)
                    fetchAllDependenciesTask.dependsOn unpackTask
                                        
                    // Symlink from workspace to the unpack cache, if the dependency was unpacked to the 
                    // unpack cache (as opposed to unpacked directly to the workspace).
                    Task symlinkToCacheTask = versionInfo.getSymlinkTaskIfUnpackingToCache(project)
                    if (symlinkToCacheTask != null) {
                        rebuildSymlinksTask.dependsOn symlinkToCacheTask
                        deleteSymlinksTask.doLast {
                            Symlink.delete(versionInfo.getTargetPathInWorkspace(project))
                        }
                    }
                    
                    pathsForPackedDependencies.add(Helper.relativizePath(versionInfo.getTargetPathInWorkspace(project), project.rootProject.projectDir))
                }
            }
            
            pathsForPackedDependencies = pathsForPackedDependencies.unique()
            final PackedDependencyHandler packedDependenciesDefault = project.packedDependenciesDefault as PackedDependencyHandler
            if (project == project.rootProject && packedDependenciesDefault.shouldCreateSettingsFile()) {
                Task t = project.task("createSettingsFileForPackedDependencies", type: DefaultTask) { Task task ->
                    task.doLast {
                        SettingsFileHelper.writeSettingsFile(new File(project.projectDir, "settings.gradle"), pathsForPackedDependencies)
                    }
                }
                fetchAllDependenciesTask.dependsOn t
            }
            
            if (project == project.rootProject) {
                final PublishPackagesExtension publishPackages = project.publishPackages as PublishPackagesExtension
                publishPackages.defineCheckTask(unpackModules)
            }
            
            /**************************************
             * Collecting dependencies
             **************************************/
            CollectDependenciesTask collectDependenciesTask = (CollectDependenciesTask)project.task(
                "collectDependencies",
                type: CollectDependenciesTask
            ) { CollectDependenciesTask task ->
                task.group = "Dependencies"
                task.description = "Collect all non-source dependencies into a 'local_artifacts' folder."
            }
            collectDependenciesTask.initialize(project)
            
            /**************************************
             * Copying artifacts
             **************************************/
            copyArtifacts.defineCopyTask(project)
        }
    }
}


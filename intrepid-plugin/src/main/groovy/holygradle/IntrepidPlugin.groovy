package holygradle

import holygradle.buildscript.BuildScriptDependencies
import holygradle.dependencies.PackedDependencyHandler
import holygradle.packaging.PackageArtifactHandler
import holygradle.publishing.DefaultPublishPackagesExtension
import holygradle.scm.SourceControlRepositories
import holygradle.source_dependencies.CopyArtifactsHandler
import holygradle.source_dependencies.RecursivelyFetchSourceTask
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.source_dependencies.SourceDependencyTaskHandler
import holygradle.symlinks.SymlinkHandler
import holygradle.symlinks.SymlinkTask
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
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
        
        // In normal usage we should apply the 'custom-gradle-core' plugin, but in unit tests the identifier
        // is unknown so we can't apply it. But the intrepid plugin can still largely make do without it.
        try {
            project.apply plugin: 'custom-gradle-core'
        } catch (org.gradle.api.plugins.UnknownPluginException e) {
            println "Haven't applied 'custom-gradle-core' plugin."
        }
        
        /**************************************
         * Prerequisites
         **************************************/
        def prerequisites = project.extensions.findByName("prerequisites")
        if (prerequisites != null) {
            prerequisites.specify("HgAuth", {checker -> Helper.checkHgAuth(checker)})
        }
    
        /**************************************
         * Configurations
         **************************************/
        project.configurations.all { 
            resolutionStrategy.failOnVersionConflict()
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
        def deleteSymlinksTask = project.task("deleteSymlinks", type: DefaultTask) {
            group = "Dependencies"
            description = "Remove all symlinks."
        }
        def rebuildSymlinksTask = project.task("rebuildSymlinks", type: SymlinkTask) {
            group = "Dependencies"
            description = "Rebuild all symlinks."
        }
        def fetchAllDependenciesTask = project.task("fetchAllDependencies", type: RecursivelyFetchSourceTask) {
            group = "Dependencies"
            description = "Retrieves all 'packedDependencies' and 'sourceDependencies', and sets up necessary symlinks."
            dependsOn rebuildSymlinksTask
        }
        def fetchFirstLevelSourceDependenciesTask = project.task("fetchFirstLevelSourceDependencies", type: RecursivelyFetchSourceTask) {
            group = "Dependencies"
            description = "Retrieves only the first level 'sourceDependencies'."
            recursive = false
        }
        project.task("fixMercurialIni", type: DefaultTask) {
            group = "Source Dependencies"
            description = "Modify/create your mercurial.ini file as required."
            doLast {
                Helper.fixMercurialIni()
            }
        }

        // Lazy configuration is a "secret" internal feature for use by plugins.  If a task adds a ".ext.lazyConfiguration"
        // property containing a single Closure or a list of them, it/they will be executed just before that specific
        // task runs.  As long as intrepid-plugin is applied in a build script, this is available to all plugins.
        project.rootProject.gradle.taskGraph.beforeTask { Task task ->
            if (task.hasProperty('lazyConfiguration')) {
                def lazyConfig = task.lazyConfiguration
                if (lazyConfig != null) {
                    task.logger.info("Applying lazyConfiguration for task ${task.name}.")
                    if (lazyConfig instanceof List) {
                        lazyConfig.each { task.configure it }
                    } else {
                        task.configure lazyConfig
                    }
                    task.lazyConfiguration = null
                }
            }
        }

        /**************************************
         * DSL extensions
         **************************************/
        // Define the 'packedDependency' DSL for the build script.
        def packedDependencies = PackedDependencyHandler.createContainer(project)
        
        // DSL extension to specify source dependencies
        def sourceDependencies = SourceDependencyHandler.createContainer(project)
        
        // Define 'publishPackages' DSL block.
        def publishingExtension = project.extensions.getByType(PublishingExtension)
        def publishPackagesExtension = project.extensions.create(
            "publishPackages", DefaultPublishPackagesExtension, project, publishingExtension, sourceDependencies, packedDependencies
        )
        
        // Define 'sourceControl' DSL.
        SourceControlRepositories.createExtension(project)
        
        // Define 'symlinks' DSL block.
        def symlinks = SymlinkHandler.createExtension(project)
        
        // Define 'packageArtifacts' DSL for the build script.
        def packageArtifacts = PackageArtifactHandler.createContainer(project)
        
        // Define 'copyArtifacts' DSL
        def copyArtifacts = CopyArtifactsHandler.createExtension(project)
        
        // Define 'sourceDependencyTasks' DSL
        def sourceDependencyTasks = SourceDependencyTaskHandler.createContainer(project)
    
        // Prepare dependencies that this plugin will require.
        def buildScriptDependencies = BuildScriptDependencies.initialize(project)
        
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
                def hgUnpackTask = buildScriptDependencies.getUnpackTask("Mercurial")
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
        project.configurations {
            everything
        }
        project.gradle.projectsEvaluated {
            def allConfs = project.configurations.collect { it }          
            project.configurations {
                allConfs.each { conf ->
                    if (conf.name != "everything" && !conf.name.startsWith("private")) {
                        everything.extendsFrom conf
                    }
                }
            }
        }
        
        /**************************************
         * Source dependencies
         **************************************/        
        project.gradle.projectsEvaluated {
            // Do we have any Hg source dependencies? Need to check for Hg prerequisite.
            if (sourceDependencies.findAll{it.protocol == "hg"}.size() > 0) {
                fetchAllDependenciesTask.doFirst {
                    prerequisites.check("HgAuth")
                }
                fetchFirstLevelSourceDependenciesTask.doFirst { 
                    prerequisites.check("HgAuth")
                }
            }
            
            def buildTasks = Helper.getProjectBuildTasks(project)
            
            // For each source dependency, create a suitable task and link it into the 
            // fetchAllDependencies task.
            sourceDependencies.each { sourceDep ->
                if (sourceDep.usePublishedVersion) {
                    def depCoord = sourceDep.getDynamicPublishedDependencyCoordinate(project)
                    // println ":${project.name} - using published version of ${sourceDep.name} - ${depCoord}"
                    def packedDep = new PackedDependencyHandler(sourceDep.name, project, depCoord, sourceDep.publishingHandler.configurations)
                    packedDependencies.add(packedDep)
                } else {
                    def fetchTask = sourceDep.createFetchTask(project, buildScriptDependencies)
                    fetchAllDependenciesTask.dependsOn fetchTask
                    fetchFirstLevelSourceDependenciesTask.dependsOn fetchTask
                                        
                    // Set up build task dependencies.
                    def depProject = project.findProject(":${sourceDep.name}")
                    if (depProject != null) {
                        def subBuildTasks = Helper.getProjectBuildTasks(depProject)
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
            def sourceDeps = Helper.getTransitiveSourceDependencies(project)
            sourceDeps.each { sourceDep ->
                def sourceDepProj = sourceDep.getSourceDependencyProject(project)
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
                def sourceDepProj = sourceDep.getSourceDependencyProject(project)
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
                def link = it[0]
                def target = it[1]
                def linkDir = new File(project.projectDir, link)
                rebuildSymlinksTask.configure(project, linkDir, new File(project.projectDir, target))
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
            def unpackModules = UnpackModule.getAllUnpackModules(project)
            
            def pathsForPackedDependencies = []
            
            // Construct tasks to unpack the artifacts.
            unpackModules.each { module ->                
                module.versions.each { versionStr, versionInfo ->
                    // Get the unpack task which will unpack the module to the cache or directly to the workspace.
                    def unpackTask = versionInfo.getUnpackTask(project)
                    fetchAllDependenciesTask.dependsOn unpackTask
                                        
                    // Symlink from workspace to the unpack cache, if the dependency was unpacked to the 
                    // unpack cache (as opposed to unpacked directly to the workspace).
                    def symlinkToCacheTask = versionInfo.getSymlinkTaskIfUnpackingToCache(project)
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
            if (project == project.rootProject && project.packedDependenciesDefault.shouldCreateSettingsFile()) {
                def t = project.task("createSettingsFileForPackedDependencies", type: DefaultTask) {
                    doLast {
                        SettingsFileHelper.writeSettingsFile(new File(project.projectDir, "settings.gradle"), pathsForPackedDependencies)
                    }
                }
                fetchAllDependenciesTask.dependsOn t
            }
            
            if (project == project.rootProject) {
                project.publishPackages.defineCheckTask(unpackModules)
            }
            
            /**************************************
             * Collecting dependencies
             **************************************/
            def collectDependenciesTask = project.task("collectDependencies", type: CollectDependenciesTask) {
                group = "Dependencies"
                description = "Collect all non-source dependencies into a 'local_artifacts' folder."
            }
            collectDependenciesTask.initialize(project)
            
            /**************************************
             * Copying artifacts
             **************************************/
            copyArtifacts.defineCopyTask(project)
        }
    }
}


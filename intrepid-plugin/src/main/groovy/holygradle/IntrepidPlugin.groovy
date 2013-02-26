package holygradle

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.artifacts.repositories.*
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.publish.*
import org.gradle.api.publish.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*

class IntrepidPlugin implements Plugin<Project> {        
    void apply(Project project) {
        /**************************************
         * Apply other plugins
         **************************************/
        project.apply plugin: 'ivy-publish'
        project.apply plugin: 'custom-gradle-core'
        
        /**************************************
         * Prerequisites
         **************************************/
        def prerequisites = project.extensions.findByName("prerequisites")
        prerequisites.java("1.7")
        
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
        
        /*def commitCombo = null
        if (project == project.rootProject) {
            commitCombo = project.task("collectSourceCombo", type: DefaultTask) {
                group = "Commit"
                description = "Collect the revisions of all source code modules into a single file."
                doLast {
                    def versions = []
                    project.projectDir.listFiles().each {
                        SourceControlRepository repo = SourceControlRepositories.get(it)
                        if (repo != null) {
                            if (repo.hasLocalChanges()) {
                                println "-"*80
                                println "WARNING: The repository '${repo.getLocalDir().name}' has uncommitted changes. "
                                println "The revision numbers captured will not take account of these changes."
                                println "-"*80
                            }
                            versions.add("${repo.getLocalDir().name}->${repo.getUrl()}->${repo.getRevision()}")
                        }
                    }
                    println "Revisions:"
                    versions.each { println "  $it" }
                    
                    new File("source_revisions.txt").write(versions.join("\n"))
                }
            }
        }*/
        
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
        
        // Define 'symlinks' DSL block.
        def symlinks = SymlinkHandler.createExtension(project)
        
        // Define 'packageArtifacts' DSL for the build script.
        def packageArtifacts = PackageArtifactHandler.createContainer(project)
        
        // Define 'sourceDependencyTasks' DSL
        def sourceDependencyTasks = SourceDependencyTaskHandler.createContainer(project)
    
        // Prepare dependencies that this plugin will require.
        def buildScriptDependencies = BuildScriptDependencies.initialize(project)
        buildScriptDependencies.add("hg-credential-store", false)
        
        // Define a task for extracting sevenZip, only for the root project.
        if (project == project.rootProject) {
            buildScriptDependencies.add("sevenZip", true)
            fetchAllDependenciesTask.dependsOn buildScriptDependencies.getUnpackTask("sevenZip")
        }
        
        boolean offline = project.gradle.startParameter.isOffline()
        
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
        
        // Create an internal 'createPublishNotes' task to create some text files to be included in all
        // released packages.
        SourceControlRepository repo = SourceControlRepositories.get(project.projectDir)
        def createPublishNotesTask = null
        if (repo != null) {
            createPublishNotesTask = project.task("createPublishNotes", type: DefaultTask) {
                group = "Publishing"
                description = "Creates 'build_info' directory which will be included in published packages."
                doLast {
                    def buildInfoDir = new File(project.projectDir, "build_info")
                    if (buildInfoDir.exists()) {
                        buildInfoDir.deleteDir()
                    }
                    buildInfoDir.mkdir()
                    
                    new File(buildInfoDir, "source_url.txt").write(repo.getUrl())
                    new File(buildInfoDir, "source_revision.txt").write(repo.getRevision())
                    
                    def BUILD_NUMBER = System.getenv("BUILD_NUMBER")
                    if (BUILD_NUMBER != null) {
                        new File(buildInfoDir, "build_number.txt").write(BUILD_NUMBER)
                    }
                    
                    def BUILD_URL = System.getenv("BUILD_URL")
                    if (BUILD_URL != null) {
                        new File(buildInfoDir, "build_url.txt").write(BUILD_URL)
                    }
                }
            }
        }                    
        
        // Create 'packageXxxxYyyy' tasks for each entry in 'packageArtifacts' in build script.
        project.gradle.projectsEvaluated {
            // Define a 'buildScript' package which is part of the 'everything' configuration.
            project.packageArtifacts {
                buildScript {
                    include project.buildFile.name
                    configuration = "everything"
                }
            }
            
            def packageEverythingTask = null
            if (packageArtifacts.size() > 0) {
                packageEverythingTask = project.task("packageEverything", type: DefaultTask) {
                    group = "Publishing"
                    description = "Creates all zip packages for project '${project.name}'."
                }
            }
            packageArtifacts.each { packArt ->
                def packageTask = packArt.definePackageTask(project, createPublishNotesTask)
                project.artifacts.add(packArt.getConfigurationName(), packageTask)
                packageEverythingTask.dependsOn(packageTask)
            }
        }
                
        /**************************************
         * Source dependencies
         **************************************/        
        project.gradle.projectsEvaluated {
            // Do we have any Hg source dependencies? Need to check for Hg prerequisite.
            if (sourceDependencies.findAll{it.protocol == "hg"}.size() > 0) {
                fetchAllDependenciesTask.doFirst { 
                    Helper.verifyHgPrerequisites(project)
                }
                fetchFirstLevelSourceDependenciesTask.doFirst { 
                    Helper.verifyHgPrerequisites(project)
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
                    Helper.deleteSymlink(linkDir)
                }
            }
        }
        
        /**************************************
         * Unpacking stuff
         **************************************/
        
        // One 'unpack' task per 'packedDependency' block of DSL in the build script.
        project.gradle.projectsEvaluated {
            // Define dependencies for all entries in the 'packedDependencies' DSL in the build script.
            packedDependencies.each { dep ->
                def depGroup = dep.getGroupName()
                def depName = dep.getDependencyName()
                def depVersion = dep.getVersionStr()
                
                dep.getConfigurations().each { conf ->
                    def fromConf = conf[0]
                    def toConf = conf[1]
                    project.dependencies.add(
                        fromConf, 
                        new DefaultExternalModuleDependency(depGroup, depName, depVersion, toConf)
                    )
                }
            }

            // For each artifact that is listed as a dependency, determine if we need to unpack it.
            def unpackModules = UnpackModule.getAllUnpackModules(project)
                        
            // Check if we have artifacts for each entry in packedDependency.
            if (!offline) {
                packedDependencies.each { dep -> 
                    if (unpackModules.count { it.name == dep.getDependencyName() } == 0) {
                        throw new RuntimeException("No artifacts detected for dependency '${dep.name}'. Check that you have correctly defined the configurations.")
                    }
                }
            }
            
            // Check if we need to force the version number to be included in the path in order to prevent
            // two different versions of a module to be unpacked to the same location.
            unpackModules.each { module ->
                if (module.versions.size() > 1) {
                    def noIncludesCount = 0
                    module.versions.each { versionStr, versionInfo -> if (!versionInfo.includeVersionNumberInPath) noIncludesCount++ }
                    if (noIncludesCount > 0) {
                        print "Dependencies have been detected on different versions of the module '${module.name}'. "
                        print "To prevent different versions of this module being unpacked to the same location, the version number will be " 
                        print "appended to the path as '${module.name}-<version>'. You can make this warning disappear by changing the name " 
                        print "of the packed dependency yourself to include '<version>' somewhere within the name. "
                        println "For your information, here are the details of the affected dependencies:"
                        module.versions.each { versionStr, versionInfo ->
                            print "  ${module.group}:${module.name}:${versionStr} : " + versionInfo.getIncludeInfo() + " -> "
                            versionInfo.includeVersionNumberInPath = true
                            println versionInfo.getIncludeInfo()
                        }
                    }
                }
            }
            
            // Construct tasks to unpack the artifacts.
            unpackModules.each { module ->                
                module.versions.each { versionStr, versionInfo ->
                    def packedDependency = versionInfo.getPackedDependency()
                                       
                    // Get the unpack task which will unpack the module to the cache or directly to the workspace.
                    def unpackTask = versionInfo.getUnpackTask(project)
                    fetchAllDependenciesTask.dependsOn unpackTask
                                        
                    // Symlink from workspace to the unpack cache, if the dependency was unpacked to the 
                    // unpack cache (as opposed to unpacked directly to the workspace).
                    def symlinkToCacheTask = versionInfo.getSymlinkTaskIfUnpackingToCache(project)
                    if (symlinkToCacheTask != null) {
                        rebuildSymlinksTask.dependsOn symlinkToCacheTask
                        deleteSymlinksTask.doLast {
                            Helper.deleteSymlink(versionInfo.getTargetPathInWorkspace(project))
                        }
                    }
                }
            }
            
            /**************************************
             * Collecting dependencies
             **************************************/
            def collectDependenciesTask = project.task("collectDependencies", type: CollectDependenciesTask) {
                group = "Dependencies"
                description = "Collect all non-source dependencies into a 'local_artifacts' folder."
            }
            collectDependenciesTask.initialize(project)
        }
    }
}


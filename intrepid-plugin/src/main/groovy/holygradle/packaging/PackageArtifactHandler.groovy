package holygradle

import holygradle.util.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.file.*
import org.gradle.api.tasks.bundling.*
import org.gradle.util.ConfigureUtil

class PackageArtifactHandler implements PackageArtifactDSL {
    public final String name
    public def configuration
    public PackageArtifactDSL rootPackageDescriptor = new PackageArtifactDescriptor(".")
    private def lazyConfigurations = []
    
    public static def createContainer(Project project) {
        project.extensions.packageArtifacts = project.container(PackageArtifactHandler) { packageArtifactName ->
            project.packageArtifacts.extensions.create(packageArtifactName, PackageArtifactHandler, packageArtifactName)
        }
        
        // Create an internal 'createPublishNotes' task to create some text files to be included in all
        // released packages.
        SourceControlRepository sourceRepo = SourceControlRepositories.get(project.projectDir)
        def createPublishNotesTask = null
        if (sourceRepo != null) {
            createPublishNotesTask = project.task("createPublishNotes", type: DefaultTask) {
                group = "Publishing"
                description = "Creates 'build_info' directory which will be included in published packages."
                doLast {
                    def buildInfoDir = new File(project.projectDir, "build_info")
                    if (buildInfoDir.exists()) {
                        buildInfoDir.deleteDir()
                    }
                    buildInfoDir.mkdir()
                    
                    new File(buildInfoDir, "source_url.txt").write(sourceRepo.getUrl())
                    new File(buildInfoDir, "source_revision.txt").write(sourceRepo.getRevision())
                    
                    def BUILD_NUMBER = System.getenv("BUILD_NUMBER")
                    if (BUILD_NUMBER != null) {
                        new File(buildInfoDir, "build_number.txt").write(BUILD_NUMBER)
                    }
                    
                    def BUILD_URL = System.getenv("BUILD_URL")
                    if (BUILD_URL != null) {
                        new File(buildInfoDir, "build_url.txt").write(BUILD_URL)
                    }
                    
                    def versionInfoExtension = project.extensions.findByName("versionInfo")
                    if (versionInfoExtension != null) {
                        versionInfoExtension.writeFile(new File(buildInfoDir, "versions.txt"))
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
            if (project.packageArtifacts.size() > 0) {
                packageEverythingTask = project.task("packageEverything", type: DefaultTask) {
                    group = "Publishing"
                    description = "Creates all zip packages for project '${project.name}'."
                }
            }
            
            def publishPackages = project.rootProject.extensions.findByName("publishPackages")
            if (publishPackages.getRepublishHandler() != null) {
                def repackageEverythingTask = project.task("repackageEverything", type: DefaultTask) {
                    group = "Publishing"
                    description = "As 'packageEverything' but doesn't auto-generate any files."
                    dependsOn packageEverythingTask
                }
                def republishTask = project.tasks.findByName("republish")
                if (republishTask != null) {
                    republishTask.dependsOn repackageEverythingTask
                }
            }
            project.packageArtifacts.each { packArt ->
                def packageTask = packArt.definePackageTask(project, createPublishNotesTask)
                project.artifacts.add(packArt.getConfigurationName(), packageTask)
                packageEverythingTask.dependsOn(packageTask)
            }
        }
                
        project.extensions.packageArtifacts
    }
    
    public PackageArtifactHandler() {
        this.name = null
    }
    
    public PackageArtifactHandler(String name) {
        this.name = name
        this.configuration = name
    }
    
    public void lazy(Closure lazyConfigure) {
        lazyConfigurations.add(lazyConfigure)
    }
    
    public PackageArtifactIncludeHandler include(String... patterns) {
        rootPackageDescriptor.include(patterns)
    }
    
     public void include(String pattern, Closure closure) {
        rootPackageDescriptor.include(pattern, closure)
    }
    
    public void exclude(String... patterns) {
        rootPackageDescriptor.exclude(patterns)
    }
    
    public void from(String fromLocation) {
        rootPackageDescriptor.from(fromLocation)
    }
    
    public void from(String fromLocation, Closure closure) {
        rootPackageDescriptor.from(fromLocation, closure)
    }
    
    public void to(String toLocation) {
        rootPackageDescriptor.to(toLocation)
    }
    
    public String getConfigurationName() {
        if (configuration == null) {
            throw new RuntimeException(
                "Under 'packageArtifacts' or 'packageSingleArtifact' please supply a configuration for the package '${name}'."
            );
        }
        if (configuration instanceof Configuration) {
            Configuration conf = configuration
            return conf.name
        }
        return configuration
    }
    
    public String getPackageTaskName() {
        holygradle.util.CamelCase.build("package", name)
    }
    
    public Task getPackageTask(def project) {
        project.tasks.getByName(getPackageTaskName())
    }
    
    public void includeBuildScript(Closure closure) {
        rootPackageDescriptor.includeBuildScript(closure)
    }
    
    public void includeTextFile(String path, Closure closure) {
        rootPackageDescriptor.includeTextFile(path, closure)
    }
    
    public void includeSettingsFile(Closure closure) {
        rootPackageDescriptor.includeSettingsFile(closure)
    }
    
    public void configureCopySpec(Project project, PackageArtifactDSL descriptor, CopySpec copySpec) {
        descriptor.includeHandlers.each { includeHandler ->
            def fromDir = project.projectDir
            if (descriptor.fromLocation != ".") {
                fromDir = new File(fromDir, descriptor.fromLocation)
            }
            copySpec.from(fromDir) {
                includes = includeHandler.includePatterns
                excludes = descriptor.excludes
            }
        }
        for (fromDescriptor in descriptor.fromDescriptors) {
            configureCopySpec(project, fromDescriptor, copySpec)
        }
    }
    
    public void configureCopySpec(Project project, CopySpec copySpec) {
        configureCopySpec(project, rootPackageDescriptor, copySpec)
    }
    
    public Task definePackageTask(Project project, Task createPublishNotesTask) {
        def taskName = getPackageTaskName()
        def t = project.task(taskName, type: Zip) 
        t.description = "Creates a zip file for '${name}' in preparation for publishing project '${project.name}'."
        t.inputs.property("version", project.version)
        if (createPublishNotesTask != null) {
            t.dependsOn createPublishNotesTask
        }
        t.baseName = project.name + "-" + name
        t.destinationDir = new File(project.projectDir, "packages")
        t.includeEmptyDirs = false
        def localLazyConfigurations = lazyConfigurations
        
        t.ext.lazyConfiguration = {
            // t.group = "Publishing " + project.name
            // t.classifier = name
            localLazyConfigurations.each {
                ConfigureUtil.configure(it, rootPackageDescriptor)
            }
            
            from (project.projectDir) {
                include "build_info/*.txt"
            }
            
            def taskDir = new File(destinationDir, taskName)
            if (!taskDir.exists()) {
                taskDir.mkdirs()
            }
        
            // If we're publishing then let's generate the auto-generatable files. But if we're 'republishing'
            // then just make sure that all auto-generated files are present.
            def repackageTask = project.tasks.findByName("repackageEverything")
            RepublishHandler republishHandler = null
            if (project.gradle.taskGraph.hasTask(repackageTask)) {
                republishHandler = project.rootProject.publishPackages.getRepublishHandler()
                doFirst {
                    rootPackageDescriptor.processPackageFiles(project, taskDir)
                }
            } else {
                doFirst {
                    rootPackageDescriptor.createPackageFiles(project, taskDir)
                }
            }
            
            rootPackageDescriptor.configureZipTask(t, taskDir, republishHandler)
        }
        
        t.doLast {
            println "Created '$archiveName'."
        }
        return t
    }
}
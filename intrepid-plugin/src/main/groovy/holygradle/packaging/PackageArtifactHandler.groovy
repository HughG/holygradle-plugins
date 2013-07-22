package holygradle.packaging

import holygradle.custom_gradle.VersionInfo
import holygradle.publishing.PublishPackagesExtension
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.file.*
import org.gradle.api.tasks.bundling.*
import org.gradle.util.ConfigureUtil
import holygradle.publishing.RepublishHandler
import holygradle.scm.SourceControlRepository
import holygradle.scm.SourceControlRepositories
import holygradle.custom_gradle.util.CamelCase

class PackageArtifactHandler implements PackageArtifactDSL {
    public final String name
    private String configurationName
    private final PackageArtifactDescriptor rootPackageDescriptor = new PackageArtifactDescriptor(".")
    private Collection<Closure> lazyConfigurations = []
    
    public static Collection<PackageArtifactHandler> createContainer(Project project) {
        project.extensions.packageArtifacts = project.container(PackageArtifactHandler)

        // Create an internal 'createPublishNotes' task to create some text files to be included in all
        // released packages.
        SourceControlRepository sourceRepo = SourceControlRepositories.get(project.projectDir)
        Task createPublishNotesTask = null
        if (sourceRepo != null) {
            createPublishNotesTask = project.task("createPublishNotes", type: DefaultTask) { Task it ->
                it.group = "Publishing"
                it.description = "Creates 'build_info' directory which will be included in published packages."
                it.doLast {
                    File buildInfoDir = new File(project.projectDir, "build_info")
                    if (buildInfoDir.exists()) {
                        buildInfoDir.deleteDir()
                    }
                    buildInfoDir.mkdir()
                    
                    new File(buildInfoDir, "source_url.txt").write(sourceRepo.getUrl())
                    new File(buildInfoDir, "source_revision.txt").write(sourceRepo.getRevision())
                    
                    String buildNumber = System.getenv("BUILD_NUMBER")
                    if (buildNumber != null) {
                        new File(buildInfoDir, "build_number.txt").write(buildNumber)
                    }
                    
                    String buildUrl = System.getenv("BUILD_URL")
                    if (buildUrl != null) {
                        new File(buildInfoDir, "build_url.txt").write(buildUrl)
                    }
                    
                    VersionInfo versionInfoExtension = project.extensions.findByName("versionInfo") as VersionInfo
                    if (versionInfoExtension != null) {
                        versionInfoExtension.writeFile(new File(buildInfoDir, "versions.txt"))
                    }
                }
            }
        }
        
        // Create 'packageXxxxYyyy' tasks for each entry in 'packageArtifacts' in build script.
        project.gradle.projectsEvaluated {
            NamedDomainObjectCollection<PackageArtifactHandler> packageArtifactHandlers =
                project.packageArtifacts as NamedDomainObjectCollection<PackageArtifactHandler>
            PackageArtifactHandler buildScriptHandler =
                packageArtifactHandlers.findByName("buildScript") ?: packageArtifactHandlers.create("buildScript")
            buildScriptHandler.include project.buildFile.name
            buildScriptHandler.configuration = "everything"

            Task packageEverythingTask = null
            if (packageArtifactHandlers.size() > 0) {
                packageEverythingTask = project.task("packageEverything", type: DefaultTask) {
                    group = "Publishing"
                    description = "Creates all zip packages for project '${project.name}'."
                }
            }
            
            PublishPackagesExtension publishPackages =
                project.rootProject.extensions.findByName("publishPackages") as PublishPackagesExtension
            if (publishPackages.getRepublishHandler() != null) {
                Task repackageEverythingTask = project.task("repackageEverything", type: DefaultTask) { Task repack ->
                    repack.group = "Publishing"
                    repack.description = "As 'packageEverything' but doesn't auto-generate any files."
                    repack.dependsOn packageEverythingTask
                }
                Task republishTask = project.tasks.findByName("republish")
                if (republishTask != null) {
                    republishTask.dependsOn repackageEverythingTask
                }
            }
            packageArtifactHandlers.each { packArt ->
                Task packageTask = packArt.definePackageTask(project, createPublishNotesTask)
                project.artifacts.add(packArt.getConfiguration(), packageTask)
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
        this.configurationName = name
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

    public void setConfiguration(String configuration) {
        this.configurationName = configuration
    }

    public String getConfiguration() {
        if (configurationName == null) {
            throw new RuntimeException(
                "Under 'packageArtifacts' or 'packageSingleArtifact' please supply a configuration for the package '${name}'."
            );
        }
        return configurationName
    }
    
    public String getPackageTaskName() {
        CamelCase.build("package", name)
    }
    
    public Task getPackageTask(Project project) {
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
    
    private void configureCopySpec(Project project, PackageArtifactDescriptor descriptor, CopySpec copySpec) {
        descriptor.includeHandlers.each { PackageArtifactIncludeHandler includeHandler ->
            File fromDir = project.projectDir
            if (descriptor.fromLocation != ".") {
                fromDir = new File(fromDir, descriptor.fromLocation)
            }
            copySpec.from(fromDir) { CopySpec it ->
                it.includes = includeHandler.includePatterns
                it.excludes = descriptor.excludes
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
        String taskName = getPackageTaskName()
        Zip t = (Zip)project.task(taskName, type: Zip)
        t.description = "Creates a zip file for '${name}' in preparation for publishing project '${project.name}'."
        t.inputs.property("version", project.version)
        if (createPublishNotesTask != null) {
            t.dependsOn createPublishNotesTask
        }
        t.baseName = project.name + "-" + name
        t.destinationDir = new File(project.projectDir, "packages")
        t.includeEmptyDirs = false
        Collection<Closure> localLazyConfigurations = lazyConfigurations

        PackageArtifactDescriptor localRootPackageDescriptor = rootPackageDescriptor // capture private for closure
        t.ext.lazyConfiguration = { Zip it ->
            // t.group = "Publishing " + project.name
            // t.classifier = name
            localLazyConfigurations.each {
                ConfigureUtil.configure(it, localRootPackageDescriptor)
            }
            
            it.from(project.projectDir) {
                include "build_info/*.txt"
            }
            
            File taskDir = new File(it.destinationDir, taskName)
            if (!taskDir.exists()) {
                taskDir.mkdirs()
            }
        
            // If we're publishing then let's generate the auto-generatable files. But if we're 'republishing'
            // then just make sure that all auto-generated files are present.
            Task repackageTask = project.tasks.findByName("repackageEverything")
            RepublishHandler republishHandler = null
            if (project.gradle.taskGraph.hasTask(repackageTask)) {
                final PublishPackagesExtension packages = project.rootProject.publishPackages as PublishPackagesExtension
                republishHandler = packages.republishHandler
                it.doFirst {
                    localRootPackageDescriptor.processPackageFiles(project, taskDir)
                }
            } else {
                it.doFirst {
                    localRootPackageDescriptor.createPackageFiles(project, taskDir)
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

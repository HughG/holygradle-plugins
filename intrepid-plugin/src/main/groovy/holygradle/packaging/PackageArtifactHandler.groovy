package holygradle.packaging

import holygradle.Helper
import holygradle.custom_gradle.VersionInfo
import holygradle.custom_gradle.util.CamelCase
import holygradle.io.FileHelper
import holygradle.publishing.PublishPackagesExtension
import holygradle.publishing.RepublishHandler
import holygradle.scm.SourceControlRepositories
import holygradle.scm.SourceControlRepository
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.file.CopySpec
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.util.ConfigureUtil

class PackageArtifactHandler implements PackageArtifactDSL {
    public final Project project
    public final String name
    private String configurationName
    private final PackageArtifactDescriptor rootPackageDescriptor
    private Collection<Closure> lazyConfigurations = []
    
    public static NamedDomainObjectContainer<PackageArtifactHandler> createContainer(Project project) {
        NamedDomainObjectContainer<PackageArtifactHandler> packageArtifactHandlers =
            project.container(PackageArtifactHandler) { String name ->
                new PackageArtifactHandler(project, name)
            }
        project.extensions.packageArtifacts = packageArtifactHandlers
        Task createPublishNotesTask = defineCreatePublishNotesTask(project)
        Task packageEverythingTask = project.task("packageEverything", type: DefaultTask) {
            group = "Publishing"
            description = "Creates all zip packages for project '${project.name}'."
        }
        Task repackageEverythingTask = project.task("repackageEverything", type: DefaultTask) { Task repack ->
            repack.group = "Publishing"
            repack.description = "As 'packageEverything' but doesn't auto-generate any files."
            repack.dependsOn packageEverythingTask
        }

        // Create this configuration early so we can create the buildScript PackageArtifactHandler.
        project.configurations.findByName("buildScript") ?: project.configurations.create("buildScript")
        PackageArtifactHandler buildScriptHandler =
                packageArtifactHandlers.findByName("buildScript") ?: packageArtifactHandlers.create("buildScript")
        buildScriptHandler.include project.buildFile.name
        buildScriptHandler.include project.gradle.startParameter.settingsFile?.name ?: Settings.DEFAULT_SETTINGS_FILE
        buildScriptHandler.configuration = "buildScript"
        packageArtifactHandlers.all { packArt ->
            Task packageTask = packArt.definePackageTask(createPublishNotesTask)
            try {
                project.artifacts.add(packArt.configuration, packageTask)
            } catch (UnknownConfigurationException e) {
                throw new RuntimeException(
                    "Failed to find configuration '${packArt.configuration}' in ${project} " +
                    "when adding packageArtifact entry '${packArt.name}'",
                    e
                )
            }
            packageEverythingTask.dependsOn(packageTask)
        }

        // Hook up the repackage task for this project to the republish task for the root project.  We do this in an
        // afterEvaluate block on the root project in case there's some odd evaluationDependsOn setup and the root
        // project's republish handler hasn't been created yet but will be once it's evaluated.
        project.rootProject.afterEvaluate {
            PublishPackagesExtension publishPackages =
                project.rootProject.extensions.findByName("publishPackages") as PublishPackagesExtension
            if (publishPackages.republishHandler != null) {
                Task republishTask = project.tasks.findByName("republish")
                if (republishTask != null) {
                    republishTask.dependsOn repackageEverythingTask
                }
            }
        }

        packageArtifactHandlers
    }

    private static Task defineCreatePublishNotesTask(Project project) {
        // Create an internal 'createPublishNotes' task to create some text files to be included in all
        // released packages.
        return project.task("createPublishNotes", type: DefaultTask) { Task t ->
            t.group = "Publishing"
            t.description = "Creates 'build_info' directory which will be included in published packages."
            File buildInfoDir = new File(project.projectDir, "build_info")

            // Save the build directory to an extension so it can be accessed from outside
            t.ext['buildInfoDir'] = buildInfoDir

            t.onlyIf {
                // Don't do anything if we are republishing, or we will end up deleting the original build_info and
                // replacing it with nearly no info.
                Task republishTask = project.tasks.findByName("republish")
                boolean republishing = (republishTask != null) && project.gradle.taskGraph.hasTask(republishTask)
                return !republishing
            }
            t.doLast {
                FileHelper.ensureDeleteDirRecursive(buildInfoDir)
                FileHelper.ensureMkdirs(buildInfoDir)

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
                addSourceRepositoryInfo(t, buildInfoDir)
                Collection<SourceDependencyHandler> allSourceDependencies = findAllSourceDependencies(project)
                File sourceDependenciesDir = new File(buildInfoDir, "source_dependencies")
                FileHelper.ensureMkdirs(sourceDependenciesDir)
                allSourceDependencies.each { SourceDependencyHandler handler ->
                    addSourceRepositoryInfo(t, sourceDependenciesDir, handler)
                }
            }
        }
    }

    /**
     * Collect all source dependencies keyed by absolute path, because the same repo might be a dependency of
     * several projects in the project graph, each maybe using a different relative path to end up at the same
     * place.  (If they use equivalent paths to depend on different repos, behaviour is undefined!)
     *
     * @param project The project whose set of transitive source dependencies is to be returned.
     * @return The set of transitive source dependencies for the {@code project}
     */
    private static Collection<SourceDependencyHandler> findAllSourceDependencies(Project project) {
        Map<String, SourceDependencyHandler> handlers = [:]
        collectAllSourceDependencies(project, handlers)
        return handlers.values()
    }

    private static void collectAllSourceDependencies(
        Project project,
        Map<String, SourceDependencyHandler> handlers
    ) {
        project.sourceDependencies.each { SourceDependencyHandler handler ->
            handlers[handler.absolutePath.canonicalPath] = handler
            Project srcDepProject = handler.sourceDependencyProject
            if (srcDepProject != null) {
                collectAllSourceDependencies(srcDepProject, handlers)
            }
        }
    }

    private static void addSourceRepositoryInfo(
        Task createPublishNotesTask,
        File baseDir,
        SourceDependencyHandler handler = null
    ) {
        Project project = createPublishNotesTask.project
        // For the originating project we will be passed a null handler (because we're looking at it as the root which
        // has source dependencies, not a source dependency itself), in which case we want that originating project,
        // which we can get from the task.
        File sourceRepoDir
        if (handler == null) {
            sourceRepoDir = project.projectDir
        } else {
            // We don't use handler..getSourceDependencyProject(...).projectDir, because source dependencies don't have
            // to contain a Gradle project.
            sourceRepoDir = handler.destinationDir
        }

        // We create a new SourceControlRepository instead of trying to get the "sourceControl" extension from the
        // project, because we don't want a DummySourceControl if there's no SCM info here.  Also, some source
        // dependencies may not have a project.
        SourceControlRepository sourceRepo = SourceControlRepositories.create(
            project.rootProject,
            sourceRepoDir
        )
        if (sourceRepo != null) {
            File sourceDepInfoDir
            if (handler == null) {
                // We're adding info for the originating project, so put it at top-level: baseDir = "build_info"
                sourceDepInfoDir = baseDir
            } else {
                // We're adding info for a subproject source dependency, so put it in a sub-folder; in this case,
                // baseDir = "build_info/source_dependencies"
                sourceDepInfoDir = new File(baseDir, handler.targetName)
                FileHelper.ensureMkdirs(sourceDepInfoDir)
            }

            new File(sourceDepInfoDir, "source_url.txt").write(sourceRepo.getUrl())
            new File(sourceDepInfoDir, "source_revision.txt").write(sourceRepo.getRevision())
            // The path from the createPublishNotes task's project to the source repository.
            String relativePath
            if (handler == null) {
                relativePath = '.'
            } else {
                relativePath = Helper.relativizePath(handler.absolutePath, project.projectDir)
            }
            new File(sourceDepInfoDir, "source_path.txt").write(relativePath)
        }
    }

    public PackageArtifactHandler(Project project, String name) {
        this.project = project
        this.name = name
        this.configurationName = name
        this.rootPackageDescriptor = new PackageArtifactDescriptor(project, ".")
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
    
    public void includeBuildScript(Closure closure) {
        rootPackageDescriptor.includeBuildScript(closure)
    }
    
    public void includeTextFile(String path, Closure closure) {
        rootPackageDescriptor.includeTextFile(path, closure)
    }
    
    public void includeSettingsFile(Closure closure) {
        rootPackageDescriptor.includeSettingsFile(closure)
    }

    @Override
    boolean getCreateDefaultSettingsFile() {
        return rootPackageDescriptor.getCreateDefaultSettingsFile()
    }

    @Override
    void setCreateDefaultSettingsFile(boolean create) {
        rootPackageDescriptor.setCreateDefaultSettingsFile(create)
    }

    private void doConfigureCopySpec(PackageArtifactDescriptor descriptor, CopySpec copySpec) {
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
            doConfigureCopySpec(fromDescriptor, copySpec)
        }
    }
    
    public void configureCopySpec(CopySpec copySpec) {
        doConfigureCopySpec(rootPackageDescriptor, copySpec)
    }
    
    public AbstractArchiveTask definePackageTask(Task createPublishNotesTask) {
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
        t.ext['lazyConfiguration'] = { Zip it ->
            // t.group = "Publishing " + project.name
            // t.classifier = name
            localLazyConfigurations.each {
                ConfigureUtil.configure(it, localRootPackageDescriptor)
            }
            
            it.from(project.projectDir) {
                include "build_info/**"
            }
            
            File taskDir = new File(it.destinationDir, taskName)
            FileHelper.ensureMkdirs(taskDir, "as output folder for publish notes ${taskDir}")
        
            // If we're publishing then let's generate the auto-generatable files. But if we're 'republishing'
            // then just make sure that all auto-generated files are present.
            Task repackageTask = project.tasks.findByName("repackageEverything")
            RepublishHandler republishHandler = null
            if (project.gradle.taskGraph.hasTask(repackageTask)) {
                final PublishPackagesExtension packages = project.rootProject.publishPackages as PublishPackagesExtension
                republishHandler = packages.republishHandler
                it.doFirst {
                    localRootPackageDescriptor.processPackageFiles(taskDir)
                }
            } else {
                it.doFirst {
                    localRootPackageDescriptor.createPackageFiles(taskDir)
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

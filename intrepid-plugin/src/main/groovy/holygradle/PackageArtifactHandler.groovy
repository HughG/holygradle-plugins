package holygradle

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.bundling.*
import org.gradle.util.ConfigureUtil

interface PackageArtifactDSL {
    void include(String... patterns)
    
    void includeBuildScript(Closure closure)
    
    void includeTextFile(String path, Closure closure)
    
    void includeSettingsFile(Closure closure)
    
    void exclude(String... patterns)
    
    void from(String fromLocation)
    
    void from(String fromLocation, Closure closure)
    
    void to(String toLocation)
}

class PackageArtifactDescriptor implements PackageArtifactDSL {
    public def includes = []
    public def excludes = [".gradle", "build.gradle", "packages/**/*", "packages/*"]
    public String fromLocation
    public String toLocation
    public def fromDescriptors = []
    public PackageArtifactBuildScriptHandler buildScriptHandler = null
    private def textFileHandlers = []
    
    public PackageArtifactDescriptor(String projectRelativePath) {
        fromLocation = projectRelativePath
        toLocation = projectRelativePath
    }
    
    public void include(String... patterns) {
        for (p in patterns) {
            includes.add(p)
            if (excludes.contains(p)) {
                excludes.remove(p)
            }
        }
    }
    
    public void includeBuildScript(Closure closure) {
        if (buildScriptHandler == null) {
            buildScriptHandler = new PackageArtifactBuildScriptHandler()
            ConfigureUtil.configure(closure, buildScriptHandler)
        } else {
            throw new RuntimeException("Can only include one build script per package.")
        }
    }
    
    public void includeTextFile(String path, Closure closure) {
        def textFileHandler = new PackageArtifactTextFileHandler(path)
        ConfigureUtil.configure(closure, textFileHandler)
        textFileHandlers.add(textFileHandler)
    }
    
    public void includeSettingsFile(Closure closure) {
        def settingsFileHandler = new PackageArtifactSettingsFileHandler("settings.gradle")
        ConfigureUtil.configure(closure, settingsFileHandler)
        textFileHandlers.add(settingsFileHandler)
    }
    
    public void exclude(String... patterns) {
        for (p in patterns) {
            excludes.add(p)
        }
    }
    
    public void from(String fromLocation) {
        this.fromLocation = fromLocation
    }
    
    public void from(String fromLocation, Closure closure) {
        def from = new PackageArtifactDescriptor(this.fromLocation + "/" + fromLocation)
        fromDescriptors.add(from)
        ConfigureUtil.configure(closure, from)
    }
    
    public void to(String toLocation) {
        this.toLocation = toLocation
    }
    
    public void createPackageFiles(Project project, File parentDir) {
        if (buildScriptHandler != null && buildScriptHandler.buildScriptRequired()) {
            buildScriptHandler.createBuildScript(project, new File(parentDir, "build.gradle"))
        }
        for (textFileHandler in textFileHandlers) {
            textFileHandler.writeFile(parentDir)
        }
    }
    
    public def collectPackageFilePaths() {
        def paths = []
        if (buildScriptHandler != null && buildScriptHandler.buildScriptRequired()) {
            if (toLocation == ".") {
                paths.add("build.gradle")
            } else {
                paths.add("${toLocation}/build.gradle")
            }
        }
        for (textFileHandler in textFileHandlers) {
            if (toLocation == ".") {
                paths.add(textFileHandler.name)
            } else {
                paths.add("${toLocation}/${textFileHandler.name}")
            }
        }
        for (fromDescriptor in fromDescriptors) {
            fromDescriptor.collectPackageFilePaths().each {
                paths.add(it)
            }
        }
        paths
    }
}

class PackageArtifactHandler implements PackageArtifactDSL {
    public final String name
    public def configuration
    public PackageArtifactDSL rootPackageDescriptor = new PackageArtifactDescriptor(".")
    
    public static def createContainer(Project project) {
        project.extensions.packageArtifacts = project.container(PackageArtifactHandler) { packageArtifactName ->
            project.packageArtifacts.extensions.create(packageArtifactName, PackageArtifactHandler, packageArtifactName)
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
    
    public void include(String... patterns) {
        rootPackageDescriptor.include(patterns)
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
        Helper.MakeCamelCase("package", name)
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
    
    private void configureZipTask(PackageArtifactDSL descriptor, Task zipTask) {
        if (descriptor.includes.size() > 0) {
            zipTask.from(descriptor.fromLocation) {
                if (descriptor.toLocation != ".") {
                    into(descriptor.toLocation)
                }
                includes = descriptor.includes
                excludes = descriptor.excludes
            }
        }
        for (fromDescriptor in descriptor.fromDescriptors) {
            configureZipTask(fromDescriptor, zipTask)
        }
    }
    
    public Task definePackageTask(def project, Task createPublishNotesTask) {
        def taskName = getPackageTaskName()
        def t = project.task(taskName, type: Zip) 
        
        if (createPublishNotesTask != null) {
            t.dependsOn createPublishNotesTask
        }
        
        t.inputs.property("version", project.version)
        t.destinationDir = new File(project.projectDir, "packages")
        t.baseName = project.name + "-" + name
        t.includeEmptyDirs = false
        t.description = "Creates a zip file for '${name}' in preparation for publishing project '${project.name}'."
        // t.group = "Publishing " + project.name
        // t.classifier = name
        
        configureZipTask(rootPackageDescriptor, t)
        
        t.include "build_info/*.txt"
                
        def taskDir = new File(t.destinationDir, taskName)
        if (!taskDir.exists()) {
            taskDir.mkdirs()
        }
        t.doFirst {
            this.rootPackageDescriptor.createPackageFiles(project, taskDir)
        }
        t.from(taskDir.path) {
            include this.rootPackageDescriptor.collectPackageFilePaths()
            excludes = []
        }
        t.doLast {
            println "Created '$archiveName'."
        }
        return t
    }
}
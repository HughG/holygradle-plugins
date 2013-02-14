package holygradle

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.bundling.*
import org.gradle.util.ConfigureUtil

interface PackageArtifactDSL {
    void include(String... patterns)
    
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
}

class PackageArtifactHandler implements PackageArtifactDSL {
    public final String name
    private PackageArtifactBuildScriptHandler buildScriptHandler
    private def textFileHandlers = []
    public def configuration
    private PackageArtifactDSL rootPackageDescriptor = new PackageArtifactDescriptor(".")
    
    public static def createContainer(Project project) {
        project.extensions.packageArtifacts = project.container(PackageArtifactHandler) { packageArtifactName ->
            def packageArtifact = project.packageArtifacts.extensions.create(packageArtifactName, PackageArtifactHandler, packageArtifactName)  
            def packageArtifactBuildScript = project.packageArtifacts."$packageArtifactName".extensions.create("includeBuildScript", PackageArtifactBuildScriptHandler, packageArtifactName)            
            packageArtifact.initialize(packageArtifactBuildScript)
            packageArtifact
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
    
    public void initialize(PackageArtifactBuildScriptHandler buildScriptHandler) {
        this.buildScriptHandler = buildScriptHandler
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
    
    public void includeTextFile(String path, Closure closure) {
        def textFileHandler = new PackageArtifactTextFileHandler(path)
        ConfigureUtil.configure(closure, textFileHandler)
        textFileHandlers.add(textFileHandler)
    }
    
    public void createBuildScript(Project project, File buildFile) {
        buildScriptHandler.createBuildScript(project, buildFile)
    }
    
    public void createPackageFiles(Project project, File parentDir) {
        if (buildScriptHandler.buildScriptRequired()) {
            buildScriptHandler.createBuildScript(project, new File(parentDir, "build.gradle"))
        }
        for (textFileHandler in textFileHandlers) {
            textFileHandler.writeFile(parentDir)
        }
    }
    
    public def collectPackageFilePaths() {
        def paths = []
        if (buildScriptHandler.buildScriptRequired()) {
            paths.add("build.gradle")
        }
        for (textFileHandler in textFileHandlers) {
            paths.add(textFileHandler.name)
        }
        paths
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
        
        t.destinationDir = new File(project.projectDir, "packages")
        t.baseName = project.name + "-" + name
        t.includeEmptyDirs = false
        t.description = "Creates a zip file for '${name}' in preparation for publishing project '${project.name}'."
        // t.group = "Publishing " + project.name
        // t.classifier = name
        
        configureZipTask(rootPackageDescriptor, t)
        
        if (createPublishNotesTask != null) {
            t.dependsOn createPublishNotesTask
            t.from("packages/build_info") {
                into "build_info"
                include "*.txt"
            }
        }
        
        def taskDir = new File(t.destinationDir, taskName)
        if (!taskDir.exists()) {
            taskDir.mkdirs()
        }
        t.doFirst {        
            createPackageFiles(project, taskDir)
        }
        t.from(taskDir.path) {
            include collectPackageFilePaths()
            excludes = []
        }
        t.doLast {
            println "Created '$archiveName'."
        }
        return t
    }
}
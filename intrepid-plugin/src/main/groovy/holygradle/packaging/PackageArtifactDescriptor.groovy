package holygradle

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.bundling.*
import org.gradle.util.ConfigureUtil

class PackageArtifactDescriptor implements PackageArtifactDSL {
    public def includeHandlers = []
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
    
    public PackageArtifactIncludeHandler include(String... patterns) {
        def includeHandler = new PackageArtifactIncludeHandler(patterns)
        includeHandlers.add(includeHandler)
        
        for (p in patterns) {
            if (excludes.contains(p)) {
                excludes.remove(p)
            }
        }
        
        includeHandler
    }
    
    public void include(String pattern, Closure closure) {
        ConfigureUtil.configure(closure, include(pattern))
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
    
    private File getTargetFile(File parentDir, String fileName) {
        if (toLocation == ".") {
            if (parentDir == null) {
                return new File(fileName)
            } else {
                return new File(parentDir, fileName)
            }
        } else {
            if (parentDir == null) {
                return new File("${toLocation}/${fileName}")
            } else {
                return new File(parentDir, "${toLocation}/${fileName}")
            }
        }
    }
    
    public void createPackageFiles(Project project, File parentDir) {
        if (buildScriptHandler != null && buildScriptHandler.buildScriptRequired()) {
            buildScriptHandler.createBuildScript(project, getTargetFile(parentDir, "build.gradle"))
        }
        for (textFileHandler in textFileHandlers) {
            textFileHandler.writeFile(getTargetFile(parentDir, textFileHandler.name))
        }
        for (fromDescriptor in fromDescriptors) {
            fromDescriptor.createPackageFiles(project, parentDir)
        }
    }
    
    private static void processFileForPackage(File sourceFile, File targetFile) {
        if (!sourceFile.exists()) {
            throw new RuntimeException("Expected file '${sourceFile.path}' to exist for repackaging.")
        }
        def sourceText = sourceFile.text
        def targetText = "// Processed...\n\n" + sourceText
        
        targetFile.parentFile.mkdirs()
        def writer = new FileWriter(targetFile)
        writer.write(targetText)
        writer.close()
    }
    
    public void processPackageFiles(Project project, File parentDir) {
        if (buildScriptHandler != null && buildScriptHandler.buildScriptRequired()) {
            processFileForPackage(
                getTargetFile(project.projectDir, "build.gradle"),
                getTargetFile(parentDir, "build.gradle")
            )
        }
        for (textFileHandler in textFileHandlers) {
            processFileForPackage(
                getTargetFile(project.projectDir, textFileHandler.name),
                getTargetFile(parentDir, textFileHandler.name)
            )
        }
        for (fromDescriptor in fromDescriptors) {
            fromDescriptor.processPackageFiles(project, parentDir)
        }
    }
    
    private def collectPackageFilePaths() {
        def paths = []
        if (buildScriptHandler != null && buildScriptHandler.buildScriptRequired()) {
            paths.add(getTargetFile(null, "build.gradle").path)
        }
        for (textFileHandler in textFileHandlers) {
            paths.add(getTargetFile(null, textFileHandler.name).path)
        }
        paths
    }
    
    public void configureZipTask(Task zipTask, File taskDir, RepublishHandler republish) {
        zipTask.from(taskDir.path) {
            include collectPackageFilePaths()
            excludes = []
            // If we're republishing then apply the search & replace rules to all of the
            // auto-generated files. It's fair to assume that they're text files and wouldn't
            // mind having filtering applied to them.
            if (republish != null) {
                republish.getReplacements().each { find, repl ->
                    filter { String line -> line.replaceAll(find, repl) }
                }
            }
        }
        
        if (includeHandlers.size() > 0) {
            def zipFromLocation = fromLocation
            if (republish != null) {
                zipFromLocation = toLocation
            }
            
            includeHandlers.each { includeHandler ->
                zipTask.from(zipFromLocation) {
                    if (toLocation != ".") {
                        into(toLocation)
                    }
                    includes = includeHandler.includePatterns
                    excludes = excludes
                    if (republish != null) {
                        // If we're republishing then apply the search & replace rules only to
                        // *.gradle and *.properties files. To be fair we should really be checking
                        // if the file is a text file and performing the filtering, but for now it's
                        // reasonable to assume that we only want filtering applied to these file types.
                        eachFile { fileInfo ->
                            if (fileInfo.name.endsWith(".gradle") || fileInfo.name.endsWith(".properties")) {
                                zipTask.logger.info("Filtering ${fileInfo.path}: ")
                                republish.getReplacements().each { find, repl ->
                                    zipTask.logger.info(" - replace '${find}' with '${repl}'.")
                                    fileInfo.filter { String line -> line.replaceAll(find, repl) }
                                }
                            }
                        }
                    }
                    includeHandler.replacements.each { find, repl ->
                        filter { String line -> line.replaceAll(find, repl) }
                    }
                }
            }
        }
        for (fromDescriptor in fromDescriptors) {
            fromDescriptor.configureZipTask(zipTask, taskDir, republish)
        }
    }
}
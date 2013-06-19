package holygradle.packaging

import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.util.ConfigureUtil
import holygradle.publishing.RepublishHandler

class PackageArtifactDescriptor implements PackageArtifactBaseDSL {
    private Collection<PackageArtifactIncludeHandler> includeHandlers = []
    private Collection<String> excludes = [".gradle", "build.gradle", "packages/**/*", "packages/*"]
    private String fromLocation
    private String toLocation
    private Collection<PackageArtifactDescriptor> fromDescriptors = []
    private PackageArtifactBuildScriptHandler buildScriptHandler = null
    private Collection<PackageArtifactTextFileHandler> textFileHandlers = []
    
    public PackageArtifactDescriptor(String projectRelativePath) {
        fromLocation = projectRelativePath
        toLocation = projectRelativePath
    }
    
    public PackageArtifactIncludeHandler include(String... patterns) {
        PackageArtifactIncludeHandler includeHandler = new PackageArtifactIncludeHandler(patterns)
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
        PackageArtifactPlainTextFileHandler textFileHandler = new PackageArtifactPlainTextFileHandler(path)
        ConfigureUtil.configure(closure, textFileHandler)
        textFileHandlers.add(textFileHandler)
    }
    
    public void includeSettingsFile(Closure closure) {
        PackageArtifactSettingsFileHandler settingsFileHandler = new PackageArtifactSettingsFileHandler("settings.gradle")
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
        PackageArtifactDescriptor from = new PackageArtifactDescriptor(this.fromLocation + "/" + fromLocation)
        fromDescriptors.add(from)
        ConfigureUtil.configure(closure, from)
    }
    
    public void to(String toLocation) {
        this.toLocation = toLocation
    }

    public Collection<PackageArtifactIncludeHandler> getIncludeHandlers() {
        return includeHandlers
    }

    public Collection<String> getExcludes() {
        return Collections.unmodifiableCollection(excludes)
    }

    public String getFromLocation() {
        return fromLocation
    }

    public Collection<PackageArtifactDescriptor> getFromDescriptors() {
        return Collections.unmodifiableCollection(fromDescriptors)
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
        String sourceText = sourceFile.text
        String targetText = "// Processed...\n\n" + sourceText
        
        targetFile.parentFile.mkdirs()
        targetFile.withWriter { Writer writer -> writer.write(targetText) }
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
    
    private Collection<File> collectPackageFilePaths() {
        Collection<File> paths = []
        if (buildScriptHandler != null && buildScriptHandler.buildScriptRequired()) {
            paths.add(getTargetFile(null, "build.gradle").path)
        }
        for (textFileHandler in textFileHandlers) {
            paths.add(getTargetFile(null, textFileHandler.name).path)
        }
        paths
    }
    
    public void configureZipTask(Zip zipTask, File taskDir, RepublishHandler republish) {
        zipTask.from(taskDir.path) { CopySpec spec ->
            spec.include collectPackageFilePaths()
            spec.excludes = []
            // If we're republishing then apply the search & replace rules to all of the auto-generated files. It's fair
            // to assume that they're text files and wouldn't mind having filtering applied to them.
            //
            // getReplacements() returns the substitutions to carry out, and "filter" configures the zip task to apply
            // them to the zipped files.
            if (republish != null) {
                republish.replacements.each { String find, String repl ->
                    spec.filter { String line -> line.replaceAll(find, repl) }
                }
            }
        }
        
        if (includeHandlers.size() > 0) {
            String zipFromLocation = fromLocation
            if (republish != null) {
                zipFromLocation = toLocation
            }

            Collection<String> descriptorExcludes = this.excludes // capture private for closure
            String descriptorToLocation = this.toLocation // capture private for closure
            includeHandlers.each { includeHandler ->
                zipTask.from(zipFromLocation) { CopySpec spec ->
                    if (descriptorToLocation != ".") {
                        into(descriptorToLocation)
                    }
                    spec.includes = includeHandler.includePatterns
                    spec.excludes = descriptorExcludes
                    if (republish != null) {
                        // If we're republishing then apply the search & replace rules only to
                        // *.gradle and *.properties files. To be fair we should really be checking
                        // if the file is a text file and performing the filtering, but for now it's
                        // reasonable to assume that we only want filtering applied to these file types.
                        eachFile { fileInfo ->
                            if (fileInfo.name.endsWith(".gradle") || fileInfo.name.endsWith(".properties")) {
                                zipTask.logger.info("Filtering ${fileInfo.path}: ")
                                republish.replacements.each { String find, String repl ->
                                    zipTask.logger.info(" - replace '${find}' with '${repl}'.")
                                    fileInfo.filter { String line -> line.replaceAll(find, repl) }
                                }
                            }
                        }
                    }
                    includeHandler.replacements.each { String find, String repl ->
                        spec.filter { String line -> line.replaceAll(find, repl) }
                    }
                }
            }
        }
        for (fromDescriptor in fromDescriptors) {
            fromDescriptor.configureZipTask(zipTask, taskDir, republish)
        }
    }
}
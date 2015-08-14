package holygradle.packaging

import holygradle.io.FileHelper
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.util.ConfigureUtil
import holygradle.publishing.RepublishHandler

class PackageArtifactDescriptor implements PackageArtifactBaseDSL {
    private final Project project
    private Collection<PackageArtifactIncludeHandler> includeHandlers = []
    private Collection<String> excludes = [".gradle", "build.gradle", "packages/**/*", "packages/*"]
    private String fromLocation
    private String toLocation
    private Collection<PackageArtifactDescriptor> fromDescriptors = []
    private PackageArtifactTextFileCollector textFileCollector

    public PackageArtifactDescriptor(Project project, String projectRelativePath) {
        this.project = project
        this.fromLocation = projectRelativePath
        this.toLocation = projectRelativePath
        this.textFileCollector = new PackageArtifactTextFileCollector(project)
    }

    @Override
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

    @Override
    public void include(String pattern, Closure closure) {
        ConfigureUtil.configure(closure, include(pattern))
    }

    @Override
    public void includeBuildScript(Closure closure) {
        textFileCollector.includeBuildScript(closure)
    }

    @Override
    public void includeTextFile(String path, Closure closure) {
        textFileCollector.includeTextFile(path, closure)
    }

    @Override
    public void includeSettingsFile(Closure closure) {
        textFileCollector.includeSettingsFile(closure)
    }

    @Override
    boolean getCreateDefaultSettingsFile() {
        return textFileCollector.createDefaultSettingsFile
    }

    @Override
    void setCreateDefaultSettingsFile(boolean create) {
        textFileCollector.createDefaultSettingsFile = create
    }

    @Override
    public void exclude(String... patterns) {
        for (p in patterns) {
            excludes.add(p)
        }
    }

    @Override
    public void from(String fromLocation) {
        this.fromLocation = fromLocation
    }

    @Override
    public void from(String fromLocation, Closure closure) {
        PackageArtifactDescriptor from = new PackageArtifactDescriptor(project, this.fromLocation + "/" + fromLocation)
        fromDescriptors.add(from)
        ConfigureUtil.configure(closure, from)
    }

    @Override
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

    public void createPackageFiles(File parentDir) {
        for (textFileHandler in textFileCollector.getAllTextFileHandlers()) {
            textFileHandler.writeFile(getTargetFile(parentDir, textFileHandler.name))
        }
        for (fromDescriptor in fromDescriptors) {
            fromDescriptor.createPackageFiles(parentDir)
        }
    }
    
    private static void processFileForPackage(File sourceFile, File targetFile) {
        if (!sourceFile.exists()) {
            throw new RuntimeException("Expected file '${sourceFile.path}' to exist for repackaging.")
        }
        String sourceText = sourceFile.text
        String targetText = "// Processed...\n\n" + sourceText

        FileHelper.ensureMkdirs(targetFile.parentFile, "as output folder for ${targetFile}")

        targetFile.withWriter { Writer writer -> writer.write(targetText) }
    }
    
    public void processPackageFiles(File parentDir) {
        for (textFileHandler in textFileCollector.getAllTextFileHandlers()) {
            processFileForPackage(
                getTargetFile(project.projectDir, textFileHandler.name),
                getTargetFile(parentDir, textFileHandler.name)
            )
        }
        for (fromDescriptor in fromDescriptors) {
            fromDescriptor.processPackageFiles(parentDir)
        }
    }
    
    private Collection<File> collectPackageFilePaths() {
        Collection<File> paths = []
        for (textFileHandler in textFileCollector.getAllTextFileHandlers()) {
            paths.add(getTargetFile(null, textFileHandler.name))
        }
        paths
    }
    
    public void configureZipTask(Zip zipTask, File taskDir, RepublishHandler republish) {
        zipTask.from(taskDir.path) { CopySpec spec ->
            Collection<String> paths = collectPackageFilePaths()*.toString()
            spec.include paths
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
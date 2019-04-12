package holygradle.packaging

import groovy.lang.Closure
import holygradle.io.FileHelper
import holygradle.publishing.RepublishHandler
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import java.io.File

class PackageArtifactDescriptor(val project: Project, projectRelativePath: String) : PackageArtifactBaseDSL {
    private val _includeHandlers = mutableListOf<PackageArtifactIncludeHandler>()
    private val _excludes = mutableListOf(".gradle", "build.gradle", "packages/**/*", "packages/*")
    var fromLocation = projectRelativePath
        private set
    var toLocation = projectRelativePath
        private set
    private val _fromDescriptors = mutableListOf<PackageArtifactDescriptor>()
    private val textFileCollector = PackageArtifactTextFileCollector(project)

    override fun include(vararg patterns: String): PackageArtifactIncludeHandler {
        val includeHandler = PackageArtifactIncludeHandler(*patterns)
        _includeHandlers.add(includeHandler)

        for (p in patterns) {
            if (_excludes.contains(p)) {
                _excludes.remove(p)
            }
        }

        return includeHandler
    }

    override fun include(pattern: String, action: Action<PackageArtifactIncludeHandler>) {
        action.execute(include(pattern))
    }

    /*override*/ fun includeBuildScript(action: Closure<in Any?>) {
        textFileCollector.includeBuildScript(action)
    }

    override fun includeBuildScript(action: Action<in PackageArtifactBuildScriptHandler>) {
        textFileCollector.includeBuildScript(action)
    }

    override fun includeTextFile(path: String, action: Action<PackageArtifactPlainTextFileHandler>) {
        textFileCollector.includeTextFile(path, action)
    }

    override fun includeSettingsFile(action: Action<PackageArtifactSettingsFileHandler>) {
        textFileCollector.includeSettingsFile(action)
    }

    override var createDefaultSettingsFile: Boolean
        get() = textFileCollector.createDefaultSettingsFile
        set(value) { textFileCollector.createDefaultSettingsFile = value }

    override fun exclude(vararg patterns: String) {
        _excludes += patterns
    }

    override fun from(fromLocation: String) {
        this.fromLocation = fromLocation
    }

    override fun from(fromLocation: String, action: Action<PackageArtifactDescriptor>) {
        val from = PackageArtifactDescriptor(project, this.fromLocation + "/" + fromLocation)
        _fromDescriptors.add(from)
        action.execute(from)
    }

    override fun to(toLocation: String) {
        this.toLocation = toLocation
    }

    val includeHandlers: Collection<PackageArtifactIncludeHandler>
        get() = _includeHandlers

    val excludes: Collection<String>
        get() = _excludes

    val fromDescriptors: Collection<PackageArtifactDescriptor>
        get() = _fromDescriptors

    fun getTargetFile(parentDir: File?, fileName: String): File {
        if (toLocation == ".") {
            if (parentDir == null) {
                return File(fileName)
            } else {
                return File(parentDir, fileName)
            }
        } else {
            if (parentDir == null) {
                return File("${toLocation}/${fileName}")
            } else {
                return File(parentDir, "${toLocation}/${fileName}")
            }
        }
    }

    fun createPackageFiles(parentDir: File) {
        for (textFileHandler in textFileCollector.allTextFileHandlers) {
            textFileHandler.writeFile(getTargetFile(parentDir, textFileHandler.name))
        }
        for (fromDescriptor in _fromDescriptors) {
            fromDescriptor.createPackageFiles(parentDir)
        }
    }
    
    fun processFileForPackage(sourceFile: File, targetFile: File) {
        if (!sourceFile.exists()) {
            throw RuntimeException("Expected file '${sourceFile.path}' to exist for repackaging.")
        }
        val sourceText = sourceFile.readText()
        val targetText = "// Processed...\n\n" + sourceText

        FileHelper.ensureMkdirs(targetFile.parentFile, "as output folder for ${targetFile}")

        targetFile.writeText(targetText)
    }
    
    fun processPackageFiles(parentDir: File) {
        for (textFileHandler in textFileCollector.allTextFileHandlers) {
            processFileForPackage(
                getTargetFile(project.projectDir, textFileHandler.name),
                getTargetFile(parentDir, textFileHandler.name)
            )
        }
        for (fromDescriptor in _fromDescriptors) {
            fromDescriptor.processPackageFiles(parentDir)
        }
    }
    
    fun collectPackageFilePaths(): Collection<File> {
        val paths = mutableListOf<File>()
        for (textFileHandler in textFileCollector.allTextFileHandlers) {
            paths.add(getTargetFile(null, textFileHandler.name))
        }
        return paths
    }
    
    fun configureZipTask(zipTask: Zip, taskDir: File, republish: RepublishHandler?) {
        zipTask.from(taskDir.path) { spec ->
            val paths = collectPackageFilePaths().map { it.toString() }
            spec.include(paths)
            spec.setExcludes(listOf())
            // If we're republishing then apply the search & replace rules to all of the auto-generated files. It's fair
            // to assume that they're text files and wouldn't mind having filtering applied to them.
            //
            // getReplacements() returns the substitutions to carry out, and "filter" configures the zip task to apply
            // them to the zipped files.
            if (republish != null) {
                for ((find, repl) in republish.replacements) {
                    spec.filter { line -> line.replace(find, repl) }
                }
            }
        }
        
        if (_includeHandlers.isNotEmpty()) {
            var zipFromLocation = fromLocation
            if (republish != null) {
                zipFromLocation = toLocation
            }

            for (includeHandler in _includeHandlers) {
                zipTask.from(zipFromLocation) { spec ->
                    if (toLocation != ".") {
                        zipTask.into(toLocation)
                    }
                    spec.setIncludes(includeHandler.includePatterns)
                    spec.setExcludes(_excludes)
                    if (republish != null) {
                        // If we're republishing then apply the search & replace rules only to
                        // *.gradle and *.properties files. To be fair we should really be checking
                        // if the file is a text file and performing the filtering, but for now it's
                        // reasonable to assume that we only want filtering applied to these file types.
                        zipTask.eachFile { fileInfo ->
                            if (fileInfo.name.endsWith(".gradle") || fileInfo.name.endsWith(".properties")) {
                                zipTask.logger.info("Filtering ${fileInfo.path}: ")
                                republish.replacements.forEach { (find, repl) ->
                                    zipTask.logger.info(" - replace '${find}' with '${repl}'.")
                                    fileInfo.filter { line -> line.replace(find, repl) }
                                }
                            }
                        }
                    }
                    for ((find, repl) in includeHandler.replacements) {
                        spec.filter { line -> line.replace(find, repl) }
                    }
                }
            }
        }
        for (fromDescriptor in _fromDescriptors) {
            fromDescriptor.configureZipTask(zipTask, taskDir, republish)
        }
    }
}
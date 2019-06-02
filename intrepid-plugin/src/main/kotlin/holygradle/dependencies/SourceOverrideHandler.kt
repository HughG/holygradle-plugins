package holygradle.dependencies

import groovy.lang.Closure
import groovy.util.XmlNodePrinter
import groovy.util.XmlParser
import groovy.xml.Namespace
import holygradle.Helper
import holygradle.apache.ivy.groovy.IvyDependencyNode
import holygradle.apache.ivy.groovy.IvyNode
import holygradle.apache.ivy.groovy.asIvyModule
import holygradle.artifacts.toModuleVersionIdentifier
import holygradle.io.FileHelper
import holygradle.kotlin.dsl.KotlinClosure1
import holygradle.kotlin.dsl.container
import holygradle.kotlin.dsl.getValue
import holygradle.process.ExecHelper
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import java.io.File
import java.io.FileOutputStream

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val HOLY_GRADLE_NAMESPACE = "https://holygradle.butbucket.org/ivy-ext/1/"
private const val HOLY_GRADLE_NAMESPACE_NAME = "holygradle"
private const val SOURCE_OVERRIDES_EXTENSION_NAME = "sourceOverrides"
internal val HOLY_GRADLE_IVY_NAMESPACE = Namespace(HOLY_GRADLE_NAMESPACE, HOLY_GRADLE_NAMESPACE_NAME)
internal var IvyDependencyNode.sourcePath: String? by IvyNode.NodeNamespacedAttribute(HOLY_GRADLE_IVY_NAMESPACE)

class SourceOverrideHandler(
        val name: String,
        private val project: Project
) {
    private var dependencyId: ModuleVersionIdentifier? = null
    var from: String? = null
        private set
    private var _dummyVersionString: String? = null
    internal var dummyVersionString: String
        get() =
            _dummyVersionString
                    ?: throw RuntimeException("You must call the sourceOverride method of the SourceOverrideHandler " +
                    "before trying to read the dummy version string")
        set(value) { _dummyVersionString = value }
    private var ivyFileGenerator: (SourceOverrideHandler.() -> File?)? = null
    private var hasGeneratedDummyModuleFiles: Boolean = false

    companion object {
        @JvmStatic
        fun createContainer(project: Project): Collection<SourceOverrideHandler> {
            val container = project.container { name: String ->
                SourceOverrideHandler(name, project)
            }
            project.extensions.add(SOURCE_OVERRIDES_EXTENSION_NAME, container)
            return container
        }
    }

    fun dependency(dep: String) {
        initialiseDependencyId(dep)
    }

    fun from(override: String) {
        val from = if (File(override).isAbsolute) {
            override
        } else {
            File(project.projectDir, override).canonicalPath
        }
        this.from = from
        dummyVersionString = Helper.convertPathToVersion(from)
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // API method for use in build scripts.
    fun ivyFileGenerator(generator: Closure<File?>) {
        ivyFileGenerator = { generator.call() }
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // API method for use in build scripts.
    fun ivyFileGenerator(generator: SourceOverrideHandler.() -> File?) {
        ivyFileGenerator = generator
    }

    fun getIvyFileGenerator(): Closure<File?>? {
        val generator = ivyFileGenerator
        return if (generator == null) null else KotlinClosure1(generator)
    }

    val ivyFile: File by lazy<File> {
        val from = this.from
            ?: throw RuntimeException(
                    "Cannot generate dependency files for source override '${name}': the 'from' location has not been set"
            )
        val defaultIvyXmlFile = File(from, "build/holygradle/flat-ivy.xml")
        val gradleWrapperScript = File(from, "gradlew.bat")
        val generateSourceOverrideDetailsScript = File(from, "generateSourceOverrideDetails.bat")
        var sourceOverrideIvyFile: File? = null

        if (project.hasProperty("useCachedSourceOverrideFiles")) {
            project.logger.info("Skipping generation of fresh ivy file for ${dependencyCoordinate}")
            sourceOverrideIvyFile = defaultIvyXmlFile
        }

        // First check the cache.
        val generator = ivyFileGenerator
        when {
            sourceOverrideIvyFile != null -> {
                project.logger.info("Using cached ivy file for ${dependencyCoordinate}")
            }
            generator != null -> {
                // Otherwise use the generator function, if defined.
                project.logger.info("Using custom ivy file generator for ${dependencyCoordinate}")
                val ivyXmlFile = this.generator()
                if (ivyXmlFile == null) {
                    throw RuntimeException(
                            "ivyFileGenerator for source override ${name} in ${project} must return a valid filenames, " +
                                    "but returned '${ivyXmlFile}'."
                    )
                }
                sourceOverrideIvyFile = ivyXmlFile
            }
            generateSourceOverrideDetailsScript.exists() -> {
                // Otherwise try to run a user provided Ivy file generator, if it exists.
                project.logger.info("Using standard ivy file generator batch script for ${dependencyCoordinate}")
                project.logger.info("Batch File: ${generateSourceOverrideDetailsScript.canonicalPath}")

                ExecHelper.execute(project.logger, project::exec) {
                    workingDir(from)
                    executable(generateSourceOverrideDetailsScript.canonicalPath)
                }
                sourceOverrideIvyFile = defaultIvyXmlFile
            }
            gradleWrapperScript.exists() -> {
                // Otherwise try to run gradlew.bat, if it exists.
                project.logger.info("Using gradlew.bat ivy file generation for ${dependencyCoordinate}")
                project.logger.info("${generateSourceOverrideDetailsScript.canonicalPath} not found")

                ExecHelper.execute(project.logger, project::exec) {
                    workingDir(from)
                    executable(gradleWrapperScript.canonicalPath)
                    args("-PrecordAbsolutePaths", "generateDescriptorFileForIvyPublication", "summariseAllDependencies")
                }
                sourceOverrideIvyFile = defaultIvyXmlFile
            }
            else -> throw RuntimeException(
                    "No Ivy file generation available for '${name}'. " +
                            "Please ensure your source override contains a generateSourceOverrideDetails.bat, " +
                            "or a compatible gradlew.bat, or else provide a custom generation method in your build.gradle."
            )
        }

        project.logger.info("generateIvyFile result: ${sourceOverrideIvyFile}")

        // Lastly, check the file really exists.
        if (!sourceOverrideIvyFile.exists()) {
            throw RuntimeException(
                    "For source override ${name} in ${project}, Ivy file '${sourceOverrideIvyFile}' does not exist"
            )
        }

        sourceOverrideIvyFile
    }

    // This method creates the minimum set of files required to represent a "dummy" version of a module in a local
    // ivy "file://" repository.  We need to create these so that we can allow Gradle's dependency resolution to be
    // applied to the containing build and find valid modules for both dependencies in that build (from that build's
    // projects' normal repos) and in any override projects (from the local file repo we create).  We can't just try
    // to find dependencies in the override projects using repos from the containing build, because the override
    // projects might use different ivy repos -- or use some different way of finding dependencies.  Once Gradle can
    // find all these dependencies, we can make use of its ability to detect version conflicts.
    fun generateDummyModuleFiles() {
        if (hasGeneratedDummyModuleFiles) {
            return
        }
        val tempDir = File(
                project.buildDir,
                "holygradle/source_override/${groupName}/${dependencyName}/${dummyVersionString}"
        )
        FileHelper.ensureMkdirs(tempDir, "for source override dummy file repository")

        project.logger.info("Writing dummy artifact for ${dummyDependencyCoordinate}")
        val dummyArtifactFile = File(tempDir, "dummy_artifact-${dummyVersionString}.zip")
        val dummyArtifact = ZipOutputStream(FileOutputStream(dummyArtifactFile))
        // We need to add an empty entry otherwise we get an invalid ZIP file.
        dummyArtifact.putNextEntry(ZipEntry("file.txt"))
        dummyArtifact.closeEntry()
        dummyArtifact.close()
        project.logger.info("Wrote dummy artifact for ${dummyDependencyCoordinate} to '${dummyArtifactFile}'")

        val tempIvyFileName = "ivy-${dummyVersionString}.xml"
        // Explicitly check if the source exists, because project.copy will silently succeed if it doesn't.
        if (!ivyFile.exists()) {
            throw RuntimeException(
                    "Cannot copy '${ivyFile}' to '${File(tempDir, tempIvyFileName)}' because the source does not exist"
                    )
        }
        project.copy {
            it.into(tempDir)
            it.from(ivyFile.parentFile) {
                it.include(ivyFile.name)
                it.rename { tempIvyFileName }
            }
        }

        // Modify the ivy file to reflect our source
        val ivyXmlFile = File(tempDir, tempIvyFileName)
        // TODO 2019-05-29 HughG: Replace with Java or Kotlin XML parsing?
        val xmlParser = XmlParser(false, true)
        val ivyXml = xmlParser.parse(ivyXmlFile)
        val ivyModule = ivyXml.asIvyModule()

        ivyModule.info.organisation = groupName
        ivyModule.info.module = dependencyName
        ivyModule.info.revision = dummyVersionString
        // Remove all the existing artifacts
        val publications = ivyModule.publications
        publications.clear()
        ivyModule.configurations.forEach { conf ->
            publications.appendNode("artifact", mapOf(
                    "name" to "dummy_artifact",
                    "type" to "zip",
                    "ext" to "zip",
                    "conf" to conf.name
            ))
        }

        ivyModule.dependencies.forEach { dep ->
            val sourcePathValue = dep.sourcePath
            if (sourcePathValue != null) {
                dep.rev = Helper.convertPathToVersion(sourcePathValue)
            }
        }

        project.logger.info("Writing ivy file for ${dependencyCoordinate}")
        ivyXmlFile.printWriter().use { writer ->
            // TODO 2019-05-29 HughG: Replace with Java or Kotlin XML output
            val nodePrinter = XmlNodePrinter(writer)
            nodePrinter.isPreserveWhitespace = true
            nodePrinter.print(ivyXml)
        }
        project.logger.info("Wrote ivy file for ${dependencyCoordinate} to '${ivyXmlFile}'")

        hasGeneratedDummyModuleFiles = true
    }

    private fun initialiseDependencyId(dependencyCoordinate: String) {
        if (dependencyId != null) {
            throw RuntimeException("Cannot set dependency more than once")
        }
        dependencyId = dependencyCoordinate.toModuleVersionIdentifier()
        project.logger.debug("Initialised source override '${name}' with dependency coordinate ${dependencyId}" +
                "(from '${dependencyCoordinate}')")
    }

    /**
     * Throw if this handler is NOT in a valid state.  This allows callers to avoid wasting time in
     * {@link #generateDummyModuleFiles} only to find something fails later on.
     */
    internal fun checkValid() {
        if (dependencyId == null) {
            throw RuntimeException("You must call the 'dependency' method for source override '${name}'")
        }
    }

    private fun getDependencyId(): ModuleVersionIdentifier {
        checkValid()
        return dependencyId!!
    }

    val groupName: String
        get() {
            return getDependencyId().group
        }

    val dependencyName: String
        get() {
            return getDependencyId().module.name
        }

    val versionStr: String
        get() {
            return getDependencyId().version
        }

    val dependencyCoordinate: String
        get() {
            return getDependencyId().toString()
        }

    val dummyDependencyCoordinate: String
        get() {
            val depId = getDependencyId()
            return "${depId.group}:${depId.name}:${dummyVersionString}"
        }
}

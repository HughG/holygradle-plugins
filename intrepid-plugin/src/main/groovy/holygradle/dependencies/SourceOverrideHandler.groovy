package holygradle.dependencies

import groovy.xml.Namespace
import holygradle.Helper
import holygradle.io.FileHelper
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.process.ExecSpec

import java.util.regex.Matcher
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceOverrideHandler {
    public static String HOLY_GRADLE_NAMESPACE = "https://holygradle.butbucket.org/ivy-ext/1/"
    public static String HOLY_GRADLE_NAMESPACE_NAME = "holygradle"

    private String name
    private Project project
    private ModuleVersionIdentifier dependencyId = null
    private String dummyVersionString
    private String from
    private Closure<SourceOverrideHandler> ivyFileGenerator
    private boolean hasGeneratedDependencyFiles = false
    private boolean hasGeneratedDummyModuleFiles = false
    private File sourceOverrideIvyFile = null
    private File sourceOverrideDependencyFile = null

    public static Collection<SourceOverrideHandler> createContainer(Project project) {
        project.extensions.sourceOverrides = project.container(SourceOverrideHandler) { String name ->
            new SourceOverrideHandler(name, project)
        }
        project.extensions.sourceOverrides
    }

    public SourceOverrideHandler(String name, Project project) {
        this.name = name

        if (project == null) {
            throw new RuntimeException("Null project for SourceOverrideHandler for $name")
        }

        this.project = project
    }

    public String getName() {
        return name
    }

    public void dependency(String dep) {
        initialiseDependencyId(dep)
    }

    public void from(String override) {
        if (new File(override).isAbsolute()) {
            from = override
        } else {
            from = new File(project.projectDir, override).canonicalPath
        }
        dummyVersionString = Helper.convertPathToVersion(from)
    }

    public String getFrom() {
        from
    }

    @SuppressWarnings("GroovyUnusedDeclaration") // API method for use in build scripts.
    public void ivyFileGenerator(Closure<SourceOverrideHandler> generator) {
        ivyFileGenerator = generator
    }

    public Closure<SourceOverrideHandler> getIvyFileGenerator() {
        ivyFileGenerator
    }

    public File getIvyFile() {
        if (sourceOverrideIvyFile == null) {
            generateDependencyFiles()
        }
        return sourceOverrideIvyFile
    }

    public File getDependenciesFile() {
        if (sourceOverrideDependencyFile == null) {
            generateDependencyFiles()
        }
        return sourceOverrideDependencyFile
    }

    public void generateDependencyFiles() {
        if (from == null) {
            throw new RuntimeException(
                "Cannot generate dependency files for source override '${name}': the 'from' location has not been set"
            )
        }
        if (hasGeneratedDependencyFiles) {
            return
        }

        final File ivyXmlFile = new File(from, "build/publications/ivy/ivy.xml")
        final File allDependenciesXmlFile = new File(from, "all-dependencies.xml")
        final File gradleWrapperScript = new File(from, "gw.bat")
        final File generateSourceOverrideDetailsScript = new File(from, "generateSourceOverrideDetails.bat")

        if (project.hasProperty("useCachedIvyFiles")) {
            project.logger.info("Skipping generation of fresh ivy file for ${dependencyCoordinate}")
            sourceOverrideIvyFile = ivyXmlFile
            sourceOverrideDependencyFile = allDependenciesXmlFile
        }

        // First check the cache map (only the Ivy File cache, tolerate the dependency file cache being null)
        if (sourceOverrideIvyFile != null) {
            project.logger.info("Using cached ivy file for ${dependencyCoordinate}")
        } else if (getIvyFileGenerator()) {
            project.logger.info("Using custom ivy file generator for ${dependencyCoordinate}")
            def generator = getIvyFileGenerator()
            (sourceOverrideIvyFile, sourceOverrideDependencyFile) = generator(this)
        } else if (generateSourceOverrideDetailsScript.exists()) {
            // Otherwise try to run a user provided Ivy file generator
            project.logger.info("Using standard ivy file generator batch script for ${dependencyCoordinate}")
            project.logger.info("Batch File: ${generateSourceOverrideDetailsScript.canonicalPath}")
            project.exec { ExecSpec spec ->
                spec.workingDir from
                spec.executable generateSourceOverrideDetailsScript.canonicalPath
                spec.standardOutput = new ByteArrayOutputStream()
            }
            sourceOverrideIvyFile = ivyXmlFile
            sourceOverrideDependencyFile = allDependenciesXmlFile
        } else if (gradleWrapperScript.exists()) { // Otherwise try to run gw.bat
            project.logger.info("Using gw.bat ivy file generation for ${dependencyCoordinate}")
            project.logger.info("${generateSourceOverrideDetailsScript.canonicalPath} not found")

            project.exec { ExecSpec spec ->
                spec.workingDir from
                spec.executable gradleWrapperScript.canonicalPath
                spec.args "-PrecordAbsolutePaths", "generateIvyModuleDescriptor", "summariseAllDependencies"
                spec.standardOutput = new ByteArrayOutputStream()
            }
            sourceOverrideIvyFile = ivyXmlFile
            sourceOverrideDependencyFile = allDependenciesXmlFile
        } else {
            throw new RuntimeException(
                "No Ivy file generation available for '${name}'. " +
                "Please ensure your source override contains a generateSourceOverrideDetails.bat, " +
                "or a compatible gw.bat, or else provide a custom generation method in your build.gradle."
            )
        }

        project.logger.info("generateDependencyFiles result: ${sourceOverrideIvyFile}, ${sourceOverrideDependencyFile}")
        hasGeneratedDependencyFiles = true
    }

    public void generateDummyModuleFiles() {
        if (hasGeneratedDummyModuleFiles) {
            return
        }

        File tempDir = new File(
            project.buildDir,
            "holygradle/source_replacement/${groupName}/${dependencyName}/${dummyVersionString}"
        )
        FileHelper.ensureMkdirs(tempDir, "for source replacement dummy file repository")

        project.logger.info("Writing dummy artifact for ${dummyDependencyCoordinate}")
        File dummyArtifactFile = new File(tempDir, "dummy_artifact-${dummyVersionString}.zip")
        ZipOutputStream dummyArtifact = new ZipOutputStream(new FileOutputStream(dummyArtifactFile))
        dummyArtifact.putNextEntry(new ZipEntry("file.txt"))
        dummyArtifact.closeEntry()
        dummyArtifact.close()
        project.logger.info("Wrote dummy artifact for ${dummyDependencyCoordinate} to '${dummyArtifactFile}'")

        String tempIvyFileName = "ivy-${dummyVersionString}.xml"
        // Explicitly check if the source exists, because project.copy will silently succeed if it doesn't.
        if (!ivyFile.exists()) {
            throw new RuntimeException(
                "Cannot copy '${ivyFile}' to '${new File(tempDir, tempIvyFileName)}' because the source does not exist"
            )
        }
        project.copy { CopySpec copySpec ->
            copySpec.into tempDir
            copySpec.from(ivyFile.parentFile) {
                include ivyFile.name
                rename { tempIvyFileName }
            }
        }

        // Modify the ivy file to reflect our source
        def ivyXmlFile = new File(tempDir, tempIvyFileName)
        XmlParser xmlParser = new XmlParser(false, true)
        Node ivyXml = xmlParser.parse(ivyXmlFile)

        def hg = new Namespace(HOLY_GRADLE_NAMESPACE, HOLY_GRADLE_NAMESPACE_NAME)

        ivyXml.info.@organisation = groupName
        ivyXml.info.@module = dependencyName
        ivyXml.info.@revision = dummyVersionString
        // Todo: Handle the case where there is no publications node
        ivyXml.publications.replaceNode {
            publications() {
                ivyXml.configurations.conf.each { conf ->
                    artifact(name: 'dummy_artifact', type: 'zip', ext: 'zip', conf: conf.@name)
                }
            }
        }

        ivyXml.dependencies.dependency.each { Node dep ->
            if (dep.attributes()[hg.'isSource']?.toBoolean()) {
                dep.@rev = Helper.convertPathToVersion(dep.attributes()[hg.'absolutePath'] as String)
            }
        }

        project.logger.info("Writing new ivy file for ${dependencyCoordinate}")
        ivyXmlFile.withWriter { writer ->
            XmlNodePrinter nodePrinter = new XmlNodePrinter(new PrintWriter(writer))
            nodePrinter.setPreserveWhitespace(true)
            nodePrinter.print(ivyXml)
        }
        project.logger.info("Wrote new ivy file for ${dependencyCoordinate} to '${ivyXmlFile}'")

        hasGeneratedDummyModuleFiles = true
    }

    private void initialiseDependencyId(String dependencyCoordinate) {
        if (dependencyId != null) {
            throw new RuntimeException("Cannot set dependency more than once")
        }
        Matcher groupMatch = dependencyCoordinate =~ /(.+):(.+):(.+)/
        if (groupMatch.size() == 0) {
            throw new RuntimeException("Incorrect dependency coordinate format: '$dependencyCoordinate'")
        } else {
            final List<String> match = groupMatch[0] as List<String>
            dependencyId = new DefaultModuleVersionIdentifier(match[1], match[2], match[3])
            project.logger.debug "Initialised source override '${name}' with dependency coordinate ${dependencyId}" +
                "(from '${dependencyCoordinate}')"
        }
    }

    /**
     * Throw if this handler is NOT in a valid state.  This allows callers to avoid wasting time in
     * {@link #generateDummyModuleFiles} only to find something fails later on.
     */
    public void checkValid() {
        if (dependencyId == null) {
            throw new RuntimeException("You must call the 'dependency' method for source override '${name}'")
        }
    }

    private ModuleVersionIdentifier getDependencyId() {
        checkValid()
        return dependencyId
    }

    public String getGroupName() {
        getDependencyId().group
    }

    public String getDependencyName() {
        getDependencyId().module.name
    }

    public String getVersionStr() {
        getDependencyId().version
    }

    public String getDummyVersionString() {
        dummyVersionString
    }

    public String getDependencyCoordinate() {
        getDependencyId().toString()
    }

    public String getDummyDependencyCoordinate() {
        if (dummyVersionString == null) {
            throw new RuntimeException("You must call the sourceOverride method before requesting a dummy coordinate")
        }
        final ModuleVersionIdentifier depId = getDependencyId()
        "${depId.group}:${depId.name}:${dummyVersionString}"
    }
}

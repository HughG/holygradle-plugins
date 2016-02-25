package holygradle.dependencies

import groovy.xml.Namespace
import holygradle.Helper
import holygradle.io.FileHelper
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

import java.util.regex.Matcher
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceOverrideHandler {
    public static String HOLY_GRADLE_NAMESPACE = "https://holygradle.butbucket.org/ivy-ext/1/"
    public static String HOLY_GRADLE_NAMESPACE_NAME = "holygradle"

    private String name
    private Project project
    private ModuleVersionIdentifier dependencyId = null
    private ModuleVersionIdentifier dummyDependencyId = null
    private String dummyVersionString
    private String dependencyCoordinate
    private String sourceOverrideLocation
    private Closure<SourceOverrideHandler> sourceOverrideIvyFile
    private boolean hasCreatedDummyModule = false
    private File sourceOverrideIvyFileCache = null
    private File sourceOverrideDependencyFileCache = null

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

    public void sourceOverride(String override) {
        if (new File(override).isAbsolute()) {
            sourceOverrideLocation = override
        } else {
            sourceOverrideLocation = new File(project.projectDir, override).canonicalPath
        }
        dummyVersionString = Helper.convertPathToVersion(sourceOverrideLocation)
    }

    public String getSourceOverride() {
        sourceOverrideLocation
    }

    public void ivyFileGenerator(Closure<SourceOverrideHandler> generator) {
        sourceOverrideIvyFile = generator
    }

    public Closure<SourceOverrideHandler> getIvyFileGenerator() {
        sourceOverrideIvyFile
    }

    public Collection<File> getIvyFile() {

        if (project.hasProperty("useCachedIvyFiles")) {
            project.logger.info("Skipping generation of fresh ivy file for ${dependencyCoordinate}")
            return [
                new File(sourceOverride, "build/publications/ivy/ivy.xml"),
                new File(sourceOverride, "AllDependencies.xml")
            ]
        }

        // First check the cache map (only the Ivy File cache, tolerate the dependency file cache being null)
        if (sourceOverrideIvyFileCache != null) {
            project.logger.info("Using cached ivy file for ${dependencyCoordinate}")
        } else if (getIvyFileGenerator()) {
            project.logger.info("Using custom ivy file generator code for ${dependencyCoordinate}")
            def generator = getIvyFileGenerator()
            (sourceOverrideIvyFileCache, sourceOverrideDependencyFileCache) = generator(this)
        } else if (new File(sourceOverride, "generateSourceOverrideDetails.bat").exists()) {
            // Otherwise try to run a user provided Ivy file generator
            project.logger.info("Using standard ivy file generator batch script for ${dependencyCoordinate}")
            project.logger.info("Batch File: ${new File(sourceOverride, "generateSourceOverrideDetails.bat").canonicalPath}")
            project.exec {
                workingDir sourceOverride
                executable new File(sourceOverride, "generateSourceOverrideDetails.bat").canonicalPath
                standardOutput = new ByteArrayOutputStream()
            }
            sourceOverrideIvyFileCache = new File(sourceOverride, "build/publications/ivy/ivy.xml")
            sourceOverrideDependencyFileCache = new File(sourceOverride, "AllDependencies.xml")
        } else if (new File(sourceOverride, "gw.bat").exists()) { // Otherwise try to run gw.bat
            project.logger.info("Using gw.bat ivy file generation for ${dependencyCoordinate}")
            project.logger.info("${new File(sourceOverride, "generateSourceOverrideDetails.bat").canonicalPath} not found")

            project.exec {
                workingDir sourceOverride
                executable new File(sourceOverride, "gw.bat").canonicalPath
                args "-PrecordAbsolutePaths", "generateIvyModuleDescriptor", "summariseAllDependencies"
                standardOutput = new ByteArrayOutputStream()
            }
            sourceOverrideIvyFileCache = new File(sourceOverride, "build/publications/ivy/ivy.xml")
            sourceOverrideDependencyFileCache = new File(sourceOverride, "AllDependencies.xml")
        } else {
            throw new RuntimeException("No Ivy file generation available for '${name}'. Please ensure your source override contains a generateSourceOverrideDetails.bat, a compatible gw.bat or provide a custom generation method in your build.gradle.")
        }

        return [sourceOverrideIvyFileCache, sourceOverrideDependencyFileCache]
    }

    public void createDummyModuleFiles() {
        if (hasCreatedDummyModule) {
            return
        }

        def (ivyFile, dependenciesFile) = getIvyFile()
        File tempDir = new File(
            project.buildDir,
            "holygradle/source_replacement/${groupName}/${dependencyName}/${dummyVersionString}"
        )
        FileHelper.ensureMkdirs(tempDir, "Failed to create source replacement dummy file repository")

        ZipOutputStream dummyArtifact = new ZipOutputStream(
            new FileOutputStream(
                new File(tempDir, "dummy_artifact-${dummyVersionString}.zip")
            )
        )
        dummyArtifact.putNextEntry(new ZipEntry("file.txt"))
        dummyArtifact.closeEntry()
        dummyArtifact.close()

        def ivyFileName = "ivy-${dummyVersionString}.xml"

        project.copy {
            into tempDir
            from(ivyFile.parentFile) {
                include ivyFile.name
                rename { ivyFileName }
            }
        }

        // Modify the ivy file to reflect our source
        def ivyXmlFile = new File(tempDir, ivyFileName)
        XmlParser xmlParser = new XmlParser(false, true)
        Node ivyXml = xmlParser.parse(ivyXmlFile)

        def hg = new Namespace(SourceOverrideHandler.HOLY_GRADLE_NAMESPACE, SourceOverrideHandler.HOLY_GRADLE_NAMESPACE_NAME)

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

        ivyXml.dependencies.dependency.each { dep ->
            if (dep.attributes()[hg.'isSource']?.toBoolean()) {
                dep.@rev = Helper.convertPathToVersion(dep.attributes()[hg.'absolutePath'])
            }
        }

        project.logger.info("Writing new ivy file for ${dependencyCoordinate}")
        ivyXmlFile.withWriter { writer ->
            XmlNodePrinter nodePrinter = new XmlNodePrinter(new PrintWriter(writer))
            nodePrinter.setPreserveWhitespace(true)
            nodePrinter.print(ivyXml)
        }

        hasCreatedDummyModule = true
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
        }
    }

    public String getGroupName() {
        dependencyId.group
    }

    public String getDependencyName() {
        dependencyId.module.name
    }

    public String getVersionStr() {
        dependencyId.version
    }

    public String getDummyVersionString() {
        dummyVersionString
    }

    public String getDependencyCoordinate() {
        dependencyId.toString()
    }

    public String getDummyDependencyCoordinate() {
        if (dummyVersionString == null) {
            throw new RuntimeException("You must call the sourceOverride method before requesting a dummy coordinate")
        }
        "${dependencyId.group}:${dependencyId.name}:${dummyVersionString}"
    }
}

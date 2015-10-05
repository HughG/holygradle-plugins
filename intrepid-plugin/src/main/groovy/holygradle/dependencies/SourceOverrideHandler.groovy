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
    private String overrideName
    private Project project
    private ModuleVersionIdentifier dependencyId = null
    private ModuleVersionIdentifier dummyDependencyId = null
    private String dummyVersionString
    private String dependencyCoordinate
    private String sourceOverrideLocation
    private Closure sourceOverrideIvyFile
    private boolean hasCreatedDummyModule = false

    public static Collection<SourceOverrideHandler> createContainer(Project project) {
        project.extensions.sourceOverrides = project.container(SourceOverrideHandler) { String name ->
            new SourceOverrideHandler(name, project)
        }
        project.extensions.sourceOverrides
    }

    public SourceOverrideHandler(String name, Project project) {
        overrideName = name

        if (project == null) {
            throw new RuntimeException("Null project for SourceOverrideHandler for $name")
        }

        this.project = project
    }

    public String getName() {
        return overrideName
    }

    public void dependency(String dep) {
        initialiseDependencyId(dep)
    }

    public void sourceOverride(String override) {
        sourceOverrideLocation = override
        dummyVersionString = Helper.convertPathToVersion(sourceOverrideLocation)
    }

    public String getSourceOverride() {
        sourceOverrideLocation
    }

    public void ivyFileGenerator(Closure generator) {
        sourceOverrideIvyFile = generator
    }

    public Closure getIvyFileGenerator() {
        sourceOverrideIvyFile
    }

    private File sourceOverrideIvyFileCache = null
    public File getIvyFile() {

        if (project.hasProperty("useCachedIvyFiles")) {
            println("Skipping generation of fresh ivy file")
            return new File(sourceOverride, "build/publications/ivy/ivy.xml")
        }

        // First check the cache map
        if (sourceOverrideIvyFileCache != null) {
            println("Using cached ivy file")
            //sourceOverrideIvyFileCache = sourceOverrideIvyFileCache
        } else if (getIvyFileGenerator()) {
            println("Using custom ivy file generator code")
            def generator = getIvyFileGenerator()
            sourceOverrideIvyFileCache = generator()
        } else if (new File(sourceOverride, "generateIvyModuleDescriptor.bat").exists ()) {
            // Otherwise try to run a user provided Ivy file generator
            println("Using standard ivy file generator batch script")
            project.exec {
                workingDir sourceOverride
                executable "generateIvyModuleDescriptor.bat"
            }
            sourceOverrideIvyFileCache = new File(sourceOverride, "build/publications/ivy/ivy.xml")
        } else { // Otherwise try to run gw.bat
            println("Falling back to gw.bat ivy file generation")
            project.exec {
                workingDir sourceOverride
                executable "gw.bat"
                args "generateIvyModuleDescriptor"
            }
            sourceOverrideIvyFileCache = new File(sourceOverride, "build/publications/ivy/ivy.xml")
        }

        return sourceOverrideIvyFileCache
    }

    public void createDummyModuleFiles() {
        if (hasCreatedDummyModule) {
            return
        }

        File ivyFile = getIvyFile()
        File tempDir = new File(
            project.buildDir,
            "holygradle/source_replacement/${groupName}/${dependencyName}/${dummyVersionString}"
        )
        FileHelper.ensureMkdirs(tempDir, "creating source replacement dummy file repository")
        //File dummyArtifact = new File(tempDir, "dummy_artifact-${dummyVersionString}.txt")
        //dummyArtifact.text = ""

        ZipOutputStream dummyArtifact = new ZipOutputStream(
            new FileOutputStream(
                new File(tempDir, "dummy_artifact-${dummyVersionString}.zip")
            )
        )
        dummyArtifact.putNextEntry(new ZipEntry("file.txt"))
        dummyArtifact.closeEntry()
        dummyArtifact.close()

        def ivyFileName = "ivy-${dummyVersionString}.xml"

        println("Copying $ivyFile to $tempDir")
        project.copy {
            into tempDir.toString()
            from(ivyFile.parentFile.toString()) {
                include ivyFile.name.toString()
                rename 'ivy.xml', ivyFileName
            }
        }

        // Modify the ivy file to reflect our source
        def ivyXmlFile = new File(tempDir, ivyFileName)
        XmlParser xmlParser = new XmlParser(false, true)
        Node ivyXml = xmlParser.parse(ivyXmlFile)

        def hg = new Namespace("http://holy-gradle/", "holygradle")

        ivyXml.info.@organisation = groupName
        ivyXml.info.@module = dependencyName
        ivyXml.info.@revision = dummyVersionString
        ivyXml.publications.replaceNode {
            publications() {
                ivyXml.configurations.conf.each { conf ->
                    artifact(name: 'dummy_artifact', type: 'zip', ext: 'zip', conf: conf.@name)
                }
            }
        }

        // Todo: Do namespace prefixes properly
        ivyXml.dependencies.dependency.each { dep ->
            if (dep.attributes()[hg.'isSource']?.toBoolean()) {
                dep.@rev = Helper.convertPathToVersion(dep.attributes()[hg.'absolutePath'])
            }
        }

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

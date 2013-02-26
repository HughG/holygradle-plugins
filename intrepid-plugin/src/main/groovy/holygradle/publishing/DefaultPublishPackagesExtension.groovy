package holygradle

import org.gradle.api.*
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.artifacts.repositories.*
import org.gradle.api.publish.PublishingExtension
import java.io.File

public class DefaultPublishPackagesExtension implements PublishPackagesExtension {
    private final Project project
    private final PublishingExtension publishingExtension
    private final RepositoryHandler repositories
    private String nextVersionNumberStr = null
    private String autoIncrementFilePath = null
    private String environmentVariableName = null
    private def mainIvyDescriptor
    private String publishGroup = null
    private String publishName = null
    
    public DefaultPublishPackagesExtension(
        Project project,
        PublishingExtension publishingExtension,
        def sourceDependencies,
        def packedDependencies
    ) {
        this.project = project
        this.publishingExtension = publishingExtension
        this.repositories = publishingExtension.getRepositories()
        mainIvyDescriptor = publishingExtension.getPublications().getByName("ivy").getDescriptor()
        
        /*
        def taskDependencies = project.extensions.findByName("taskDependencies")
        if (taskDependencies != null) {
            def publishAll = project.task("publishAll", type: DefaultTask) {
                dependsOn "publish"
                group = "Publishing"
                description = "Publishes all publications for this module and all dependent modules."
            }
            taskDependencies.configure(publishAll)
        }
        */
        
        // Configure the publish task to deal with the version number, include source dependencies and convert 
        // dynamic dependency versions to fixed version numbers.
        project.gradle.projectsEvaluated {
            project.tasks.each { 
                if (it.name == "publish") {
                    it.group = "Publishing"
                    it.description = "Publishes all publications for this module."
                }
            }

            this.applyGroupName()
            this.applyModuleName()
            this.applyVersionNumber()
            //if (repositories.any { it instanceof AuthenticationSupported && it.getCredentials().getUsername() != null }) { 
            def publishTaskName = "publishIvyPublicationToIvyRepository"
            if (project.tasks.matching { it.name == publishTaskName }.size() > 0) {
                def publishTask = project.tasks.getByName(publishTaskName)
                this.includeSourceDependencies(project, sourceDependencies)
                this.removeUnwantedDependencies(project, packedDependencies)
                this.freezeDynamicDependencyVersions(project, packedDependencies)
                this.fixUpConflictConfigurations()
                this.removePrivateConfigurations()
                this.addDependencyRelativePaths(packedDependencies)
                publishTask.doFirst {
                    this.verifyGroupName()
                    this.verifyVersionNumber()
                }
                publishTask.doLast {
                    this.incrementVersionNumber()
                }
            }
        }
    }
    
    public void nextVersionNumber(String versionNo) {
        nextVersionNumberStr = versionNo
        applyVersionNumber()
    }
    
    public void nextVersionNumber(Closure versionNumClosure) {
        nextVersionNumber(versionNumClosure())
    }
    
    public void nextVersionNumberAutoIncrementFile(String versionNumberFilePath) {
        autoIncrementFilePath = versionNumberFilePath
        applyVersionNumber()
    }
    
    public void nextVersionNumberEnvironmentVariable(String versionNumberEnvVar) {
        environmentVariableName = versionNumberEnvVar
        applyVersionNumber()
    }
    
    public RepositoryHandler getRepositories() {
        return repositories
    }
    
    public void repositories(def configure) {
        configure.execute(repositories)
    }
    
    public String getPublishGroup() {
        publishGroup
    }
    
    public void group(String publishGroup) {
        this.publishGroup = publishGroup
    }
    
    public String getPublishName() {
        publishName
    }
    
    public void name(String publishName) {
        this.publishName = publishName
    }
    
    private File getVersionFile() {
        return new File(project.getProjectDir(), autoIncrementFilePath)
    }
    
    private String getVersionFromFile(File file)  {
        return file.text
    }
    
    private String getVersionFromFile()  {
        return getVersionFromFile(getVersionFile());
    }
    
    private String incrementVersionNumber(File versionFile) {
        def versionStr = versionFile.text
        int lastDot = versionStr.lastIndexOf('.')
        int nextVersion = versionStr[lastDot+1..-1].toInteger() + 1
        def firstChunk = versionStr[0..lastDot]
        def newVersionStr = firstChunk + nextVersion.toString()
        versionFile.write(newVersionStr)
        newVersionStr
    }
    
    public void applyGroupName() {
        if (publishGroup != null) {
            project.group = publishGroup
        }
    }
    
    public void applyModuleName() {
        if (publishName != null) {
            // Can't do anything here because of http://issues.gradle.org/browse/GRADLE-2412
        }
    }
    
    public void verifyGroupName() {
        if (project.group == null) {
            throw new RuntimeException("Please specify the group name for the published artifacts. Under 'publishPackages' something like 'group \"blah\"'.")
        }
    }
    
    public String applyVersionNumber() {
        if (nextVersionNumberStr != null) {
            project.version = nextVersionNumberStr
        } else if (autoIncrementFilePath != null) {
            File versionFile = getVersionFile();
            if (versionFile.exists()) {
                project.version = getVersionFromFile()
            }
        } else if (environmentVariableName != null) {
            String nextVersionNumber = System.getenv(environmentVariableName)
            if (nextVersionNumber != null) {
                project.version = nextVersionNumber
            }
        }
        project.version
    }
    
    public void verifyVersionNumber() {
        if (autoIncrementFilePath != null) {
            if (!getVersionFile().exists()) {
                throw new RuntimeException(
                    "'publishPackages' specifies nextVersionNumberAutoIncrementFile " + 
                    "\"${autoIncrementFilePath}\", but that file does not " +
                    "exist in the project directory: " + project.getProjectDir().getAbsolutePath()
                )
            }
        } else if (environmentVariableName != null) {
            String nextVersionNumber = System.getenv(environmentVariableName)
            if (nextVersionNumber == null) {
                throw new RuntimeException(
                    "Environment variable '${environmentVariableName}' " +
                    "is not set. This is necessary for 'publishPackages'."
                )
            }
        }
        
        int nonNullCount = 0
        if (nextVersionNumberStr != null) {
            nonNullCount++;
        }
        if (autoIncrementFilePath != null) {
            nonNullCount++;
        }
        if (environmentVariableName != null) {
            nonNullCount++;
        }
        if (nonNullCount == 0) {
            if (project.getVersion().toString() == "unspecified") {
                throw new RuntimeException(
                    "One of 'nextVersionNumber', 'nextVersionNumberAutoIncrementFile' and 'nextVersionNumberEnvironmentVariable' must be " +
                    "set on 'publishPackages' unless you set the 'version' property on the project yourself."
                )
            }
        } else if (nonNullCount > 1) {
            throw new RuntimeException(
                "Only one of 'nextVersionNumber', 'nextVersionNumberAutoIncrementFile' and 'nextVersionNumberEnvironmentVariable' should " +
                "be set on 'publishPackages'."
            )
        }
    }
    
    public String incrementVersionNumber() {
        if (autoIncrementFilePath != null) {
            incrementVersionNumber(getVersionFile());
        }
        return getCurrentVersionNumber();
    }
    
    private String getCurrentVersionNumber()  {
        String currentVersionNumber = null
        if (nextVersionNumberStr != null) {
            currentVersionNumber = nextVersionNumberStr
        } else if (autoIncrementFilePath != null) {
            currentVersionNumber = getVersionFromFile();
        } else if (environmentVariableName != null) {
            currentVersionNumber = System.getenv(environmentVariableName)
        }
        return currentVersionNumber
    }
    
    public void includeSourceDependencies(def project, def sourceDependencies) {
        mainIvyDescriptor.withXml { xml ->
            def newDependencies = []
            sourceDependencies.each { sourceDep ->
                newDependencies.addAll(sourceDep.getDependenciesForPublishing(project))
            }
            if (newDependencies.size() > 0) {
                def dependenciesNodes = xml.asNode().dependencies
                def dependenciesNode = null
                if (dependenciesNodes.size() == 0) {
                    dependenciesNode = xml.asNode().appendNode("dependencies")
                } else {
                    dependenciesNode = dependenciesNodes.get(0)
                }
                newDependencies.each { newDep ->
                    dependenciesNode.appendNode("dependency", newDep)
                }
            }
        }
    }
    
    public void removeUnwantedDependencies(def project, def packedDependencies) {
        def unwantedDependencies = []
        packedDependencies.each { packedDep ->
            if (!packedDep.shouldPublishDependency()) {
                unwantedDependencies.add(packedDep.getDependencyName())
            }
        }
        
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().dependencies.dependency.each { depNode ->
                if (unwantedDependencies.contains(depNode.@name) ||
                    depNode.@conf.startsWith("private")
                ) {
                    depNode.parent().remove(depNode)
                }
            }
        }
    }
    
    public void removePrivateConfigurations() {
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().configurations.conf.each { confNode ->
                if (confNode.@name.startsWith("private")) {
                    def parent = confNode.parent()
                    parent.remove(confNode)
                }
            }
        }
    }
    
    private static String getDependencyVersion(def project, String group, String module) {
        String version = null
        project.configurations.each { conf ->                
            conf.resolvedConfiguration.getResolvedArtifacts().each { artifact ->
                def ver = artifact.getModuleVersion().getId()
                if (ver.getGroup() == group && ver.getName() == module) {
                    version = ver.getVersion()
                }
            }
        }
        return version
    }
    
    public void freezeDynamicDependencyVersions(def project, def packedDependencies) {
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().dependencies.dependency.each { depNode ->
                if (depNode.@rev.endsWith("+")) {
                    depNode.@rev = getDependencyVersion(project, depNode.@org, depNode.@name)
                }
            }
        }
    }
    
    public void fixUpConflictConfigurations() {
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().dependencies.dependency.each { depNode ->
                def conf = depNode.@conf
                def confSplit = conf.split("->")
                if (confSplit.size() == 1) {
                    confSplit = conf.split("-&gt;")
                }
                def newConf = []
                confSplit.each { c ->
                    newConf.add(c.replaceAll("(.*)_conflict.*", { it[1] }))
                }
                depNode.@conf = newConf.join("->")
            }
        }
    }
    
    public void addDependencyRelativePaths(def packedDependencies) {
        mainIvyDescriptor.withXml { xml ->
            xml.asNode().dependencies.dependency.each { depNode ->
                def packedDep = packedDependencies.find { 
                    it.getGroupName() == depNode.@org && 
                    it.getDependencyName() == depNode.@name &&
                    it.getVersionStr() == depNode.@rev
                }
                
                if (packedDep != null) {
                    depNode.@relativePath = packedDep.getRelativePath()
                }
            }
        }
    }
}
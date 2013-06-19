package holygradle.dependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import holygradle.Helper

import java.util.regex.Matcher

class PackedDependencyHandler extends DependencyHandler {
    private Project projectForHandler
    private Collection<AbstractMap.SimpleEntry<String, String>> configurations = []
    private String dependencyGroup
    private String dependencyModule
    private String dependencyVersion
    public Boolean applyUpToDateChecks = null
    public Boolean readonly = null
    public Boolean unpackToCache = null
    public Boolean createSettingsFile = null
    public Boolean publishDependency = null
    
    public static Collection<PackedDependencyHandler> createContainer(Project project) {
        if (project == project.rootProject) {
            project.extensions.create("packedDependenciesDefault", PackedDependencyHandler, "rootDefault")
        } else {
            project.extensions.create("packedDependenciesDefault", PackedDependencyHandler, "default", project.rootProject)
        }
        project.extensions.packedDependencies = project.container(PackedDependencyHandler) { String name ->
            new PackedDependencyHandler(name, project)
        }
        project.extensions.packedDependencies
    }

    public PackedDependencyHandler(String depName) {
        super(depName)

        //throw new RuntimeException("No project for PackedDependencyHandler for $depName")

    }
    
    public PackedDependencyHandler(String depName, Project projectForHandler) {
        super(depName, projectForHandler)

        if (projectForHandler == null) {
            throw new RuntimeException("Null project for PackedDependencyHandler for $depName")
        }

        this.projectForHandler = projectForHandler
        dependencyModule = depName
    }
    
    public PackedDependencyHandler(
        String depName,
        Project projectForHandler,
        String dependencyCoordinate,
        Collection<AbstractMap.SimpleEntry<String, String>> configurations
    ) {
        this(depName, projectForHandler)
        this.projectForHandler = projectForHandler
        parseDependencyCoordinate(dependencyCoordinate)
        this.configurations = configurations
    }
    
    public PackedDependencyHandler(
        String depName,
        Project projectForHandler,
        ModuleVersionIdentifier dependencyCoordinate
    ) {
        this(depName, projectForHandler)
        dependencyGroup = dependencyCoordinate.getGroup()
        dependencyModule = dependencyCoordinate.getName()
        dependencyVersion = dependencyCoordinate.getVersion()
    }
    
    private PackedDependencyHandler getParentHandler() {
        if (projectForHandler == null) {
            null
        } else {
            projectForHandler.extensions.packedDependenciesDefault
        }
    }
    
    public void unpackToCache(boolean doUnpack) {
        this.unpackToCache = doUnpack
    }
    
    public boolean shouldUnpackToCache() {
        if (unpackToCache == null) {
            PackedDependencyHandler p = getParentHandler()
            if (p != null) {
                return p.shouldUnpackToCache()
            } else {
                return true
            }
        } else {
            return unpackToCache
        }
    }
    
    public boolean shouldCreateSettingsFile() {
        if (createSettingsFile == null) {
            PackedDependencyHandler p = getParentHandler()
            if (p != null) {
                return p.shouldCreateSettingsFile()
            } else {
                return false
            }
        } else {
            return createSettingsFile
        }
    }
    
    public boolean shouldMakeReadonly() {
        if (readonly == null) {
            PackedDependencyHandler p = getParentHandler()
            if (p != null) {
                return p.shouldMakeReadonly()
            } else {
                return !shouldApplyUpToDateChecks()
            }
        } else {
            return readonly && !shouldApplyUpToDateChecks()
        }
    }

    public boolean shouldApplyUpToDateChecks() {
        if (applyUpToDateChecks == null) {
            PackedDependencyHandler p = getParentHandler()
            if (p != null) {
                return p.shouldApplyUpToDateChecks()
            } else {
                return false
            }
        } else {
            return applyUpToDateChecks
        }
    }
    
    public boolean shouldPublishDependency() {
        if (publishDependency == null) {
            PackedDependencyHandler p = getParentHandler()
            if (p != null) {
                return p.shouldPublishDependency()
            } else {
                return true
            }
        } else {
            return publishDependency
        }
    }
    
    private void parseDependencyCoordinate(String dependencyCoordinate) {
        Matcher groupMatch = dependencyCoordinate =~ /(.+):(.+):(.+)/
        if (groupMatch.size() == 0) {
            throw new RuntimeException("Incorrect dependency coordinate format: '$dependencyCoordinate'")
        } else {
            final List<String> match = groupMatch[0] as List<String>
            dependencyGroup = match[1]
            dependencyModule = match[2]
            dependencyVersion = match[3]
        }
    }
        
    public void dependency(String dependencyCoordinate) {
        parseDependencyCoordinate(dependencyCoordinate)
    }
        
    public void configuration(String config) {
        Collection<AbstractMap<String, String>.SimpleEntry> newConfigs = []
        Helper.parseConfigurationMapping(config, newConfigs, "Formatting error for '$name' in 'packedDependencies'.")
        configurations.addAll newConfigs
        for (conf in newConfigs) {
            String fromConf = conf.key
            String toConf = conf.value
            projectForHandler.dependencies.add(
                fromConf,
                new DefaultExternalModuleDependency(dependencyGroup, dependencyModule, dependencyVersion, toConf)
            )
        }
    }
    
    public void configuration(String... configs) {
        configs.each { String config ->
            configuration(config)
        }
    }
    
    public Collection<AbstractMap.SimpleEntry<String, String>> getConfigurations() {
        configurations
    }
    
    public String getGroupName() {
        dependencyGroup
    }
    
    public String getDependencyName() {
        dependencyModule
    }
   
    public String getVersionStr() {
        dependencyVersion
    }
    
    public String getDependencyCoordinate() {
        "${dependencyGroup}:${dependencyModule}:${dependencyVersion}"
    }
    
    public boolean pathIncludesVersionNumber() {
        getTargetName().contains("<version>")
    }
    
    public String getTargetNameWithVersionNumber(String versionStr) {
        getTargetName().replace("<version>", versionStr)
    }
    
    public String getFullTargetPathWithVersionNumber(String versionStr) {
        name.replace("<version>", versionStr)
    }
}

package holygradle

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.ModuleVersionIdentifier

class PackedDependencyHandler extends DependencyHandler {
    private Project projectForHandler
    private def configurations = []
    private String dependencyCoordinate
    private String dependencyGroup
    private String dependencyModule
    private String dependencyVersion
    def applyUpToDateChecks = null
    def readonly = null
    def unpackToCache = null
    def publishDependency = null
    
    public static def createContainer(Project project) {
        def packedDependenciesDefault = null
        if (project == project.rootProject) {
            packedDependenciesDefault = project.extensions.create("packedDependenciesDefault", PackedDependencyHandler, "rootDefault")
        } else {
            packedDependenciesDefault = project.extensions.create("packedDependenciesDefault", PackedDependencyHandler, "default", project.rootProject)
        }
        project.extensions.packedDependencies = project.container(PackedDependencyHandler) { packedDepName ->
            project.packedDependencies.extensions.create(packedDepName, PackedDependencyHandler, packedDepName, project)  
        }
    }
    
    public PackedDependencyHandler(String depName) {
        super(depName)
    }
    
    public PackedDependencyHandler(String depName, Project projectForHandler) {
        super(depName, projectForHandler)
        this.projectForHandler = projectForHandler
        dependencyModule = depName
    }
    
    public PackedDependencyHandler(String depName, Project projectForHandler, String dependencyCoordinate, def configurations) {
        super(depName, projectForHandler)
        this.projectForHandler = projectForHandler
        parseDependencyCoordinate(dependencyCoordinate)
        this.configurations = configurations
    }
    
    public PackedDependencyHandler(String depName, Project projectForHandler, ModuleVersionIdentifier dependencyCoordinate) {
        super(depName, projectForHandler)
        this.projectForHandler = projectForHandler
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
            def p = getParentHandler()
            if (p != null) {
                def should = p.shouldUnpackToCache()
                return should
            } else {
                return true
            }
        } else {
            return unpackToCache
        }
    }
    
    public boolean shouldMakeReadonly() {
        if (readonly == null) {
            def p = getParentHandler()
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
            def p = getParentHandler()
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
            def p = getParentHandler()
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
        def groupMatch = dependencyCoordinate =~ /(.+):(.+):(.+)/
        if (groupMatch.size() == 0) {
            throw new RuntimeException("Incorrect dependency coordinate format: '$dependencyCoordinate'")
        } else {
            dependencyGroup = groupMatch[0][1]
            dependencyModule = groupMatch[0][2]
            dependencyVersion = groupMatch[0][3]
        }
    }
        
    public void dependency(String dependencyCoordinate) {
        parseDependencyCoordinate(dependencyCoordinate)
    }
        
    public void configuration(String config) {
        Helper.processConfiguration(config, configurations, "Formatting error for '$name' in 'packedDependencies'.")
    }
    
    public void configuration(String... configs) {
        configs.each { String config ->
            configuration(config)
        }
    }
    
    public def getConfigurations() {
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
    
    public String getDependencyCoordinate(Project project) {
        "${dependencyGroup}:${dependencyModule}:${dependencyVersion}"
    }
    
    public boolean pathIncludesVersionNumber() {
        getTargetName().contains("<version>")
    }
    
    public String getTargetNameWithVersionNumber(def versionStr) {
        getTargetName().replace("<version>", versionStr)
    }
    
    public String getFullTargetPathWithVersionNumber(def versionStr) {
        name.replace("<version>", versionStr)
    }
}
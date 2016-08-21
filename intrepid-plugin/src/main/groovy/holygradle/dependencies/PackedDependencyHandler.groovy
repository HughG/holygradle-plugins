package holygradle.dependencies

import holygradle.Helper
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

import java.util.regex.Matcher

class PackedDependencyHandler extends DependencyHandler {
    private Project projectForHandler
    private ModuleVersionIdentifier dependencyId = null
    public Boolean applyUpToDateChecks = null
    public Boolean readonly = null
    public Boolean unpackToCache = null
    private Boolean createLinkToCache = null
    public Boolean createSettingsFile = null

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
    }
    
    public PackedDependencyHandler(
        String depName,
        Project projectForHandler,
        String dependencyCoordinate,
        Collection<AbstractMap.SimpleEntry<String, String>> configurations
    ) {
        this(depName, projectForHandler)
        this.projectForHandler = projectForHandler
        initialiseDependencyId(dependencyCoordinate)
        this.configurationMappings.addAll(configurations)
    }
    
    public PackedDependencyHandler(
        String depName,
        Project projectForHandler,
        ModuleVersionIdentifier dependencyCoordinate
    ) {
        this(depName, projectForHandler)
        dependencyId = dependencyCoordinate
    }
    
    private PackedDependencyHandler getParentHandler() {
        if (projectForHandler == null) {
            null
        } else {
            projectForHandler.extensions.packedDependenciesDefault
        }
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

    public void noCreateLinkToCache() {
        createLinkToCache = false
    }

    protected Boolean getCreateLinkToCache() {
        createLinkToCache
    }

    public boolean shouldCreateLinkToCache() {
        if (createLinkToCache == null) {
            // This property is different from the others: we only use the parent (default) value if it has been
            // explicitly set.  If we were to call p.shouldCreateLinkToCache(), it would always have a fallback value
            // (of p.shouldUnpackToCache()), so we'd never get to the fallback value we want, this.shouldUnpackToCache().
            PackedDependencyHandler p = getParentHandler()
            if (p != null && p.getCreateLinkToCache() != null) {
                return p.getCreateLinkToCache()
            } else {
                return shouldUnpackToCache()
            }
        } else {
            return createLinkToCache
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
        
    public void dependency(String dependencyCoordinate) {
        initialiseDependencyId(dependencyCoordinate)
    }

    @Override
    public void configuration(String config) {
        Collection<AbstractMap<String, String>.SimpleEntry> newConfigs = []
        Helper.parseConfigurationMapping(config, newConfigs, "Formatting error for '$name' in 'packedDependencies'.")
        configurationMappings.addAll newConfigs
        ModuleVersionIdentifier id = getDependencyId()
        for (conf in newConfigs) {
            String fromConf = conf.key
            String toConf = conf.value
            projectForHandler.dependencies.add(
                fromConf,
                new DefaultExternalModuleDependency(
                    id.group, id.module.name, id.version, toConf
                )
            )
        }
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
    
    public String getDependencyCoordinate() {
        getDependencyId().toString()
    }

    public ModuleVersionIdentifier getDependencyId() {
        if (dependencyId == null) {
            throw new RuntimeException("'dependency' not set for packed dependency '${name}'")
        }
        dependencyId
    }

    public boolean pathIncludesVersionNumber() {
        targetName.contains("<version>")
    }
    
    public String getTargetNameWithVersionNumber(String versionStr) {
        targetName.replace("<version>", versionStr)
    }
    
    public String getFullTargetPathWithVersionNumber(String versionStr) {
        name.replace("<version>", versionStr)
    }
}

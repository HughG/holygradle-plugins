package holygradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency

class UnpackModule {
    public def group
    public def name
    public def versions = [:]
    
    UnpackModule(def group, def name) {
        this.group = group
        this.name = name
    }
    
    public boolean matches(ModuleVersionIdentifier moduleVersion) {
        moduleVersion.getGroup() == group && moduleVersion.getName() == name 
    }
    
    public UnpackModuleVersion getVersion(ModuleVersionIdentifier moduleVersion) {
        if (matches(moduleVersion)) {
            return versions[moduleVersion.getVersion()]
        }
        return null
    }
    
    private static File getIvyFile(ResolvedDependency resolvedDependency) {
        // Get the first artifact from the set of module artifacts.
        def firstArtifact = resolvedDependency.getModuleArtifacts().find { true }
        
        File ivyFile = null
        
        // Try to find the ivy file in the gradle cache corresponding to this artifact. This depends upon
        // the structure of the gradle cache, as well as the Ivy format.
        def ivyDirInCache = new File(firstArtifact.getFile().parentFile.parentFile.parentFile, "ivy")
        if (ivyDirInCache.exists()) {
            // Getting the file from the gradle cache.
            ivyDirInCache.traverse {
                if (it.name.startsWith("ivy-") && it.name.endsWith(".xml")) {
                    ivyFile = it
                }
            }
        }
        
        // Might we be getting the artifact from a local file-system?
        // If so then the structure is different from the cache - the zip files for a particular
        // artifact will exist in the same directory as the ivy file.
        def localIvyDir = firstArtifact.getFile().parentFile
        localIvyDir.traverse {
            if (it.name.startsWith("ivy-") && it.name.endsWith(".xml")) {
                ivyFile = it
            }
        }
        
        return ivyFile
    }
    
    private static void traverseResolvedDependencies(
        def packedDependencies,
        def unpackModules,
        Set<ResolvedDependency> dependencies
    ) {
        dependencies.each { resolvedDependency ->
            def moduleVersion = resolvedDependency.getModule().getId()
            def moduleGroup = moduleVersion.getGroup()
            def moduleName = moduleVersion.getName()
            def versionStr = moduleVersion.getVersion()
            
            PackedDependencyHandler thisPackedDep = packedDependencies.find {
                it.getDependencyName() == moduleName
            }
            
            // Is there an ivy file corresponding to this dependency? 
            File ivyFile = getIvyFile(resolvedDependency)
            if (ivyFile != null && ivyFile.exists()) {
                // If we found an ivy file then just assume that we should be unpacking it. 
                // Ideally we should look for a custom XML tag that indicates that it was published
                // by the intrepid plugin. However, that would break compatibility with lots of 
                // artifacts that have already been published.
               
                // Find or create an UnpackModule instance.
                def unpackModule = unpackModules.find { it.matches(moduleVersion) }
                if (unpackModule == null) {
                    unpackModule = new UnpackModule(moduleVersion.getGroup(), moduleName)
                    unpackModules << unpackModule
                }
                
                // Find a parent UnpackModuleVersion instance i.e. one which has a dependency on 
                // 'this' UnpackModuleVersion. There will only be a parent if this is a transitive
                // dependency. TODO: There could be more than one parent. Deal with it gracefully.
                def parentUnpackModuleVersion = null
                resolvedDependency.getParents().each { parentDependency ->
                    def parentDependencyVersion = parentDependency.getModule().getId()
                    def parentUnpackModule = unpackModules.find { it.matches(parentDependencyVersion) }
                    if (parentUnpackModule != null) {
                        parentUnpackModuleVersion = parentUnpackModule.getVersion(parentDependencyVersion)
                    }
                }
                
                // Find or create an UnpackModuleVersion instance.
                def unpackModuleVersion = null
                if (unpackModule.versions.containsKey(versionStr)) {
                    unpackModuleVersion = unpackModule.versions[versionStr]
                } else {
                    unpackModuleVersion = new UnpackModuleVersion(moduleVersion, ivyFile, parentUnpackModuleVersion, thisPackedDep)
                    unpackModule.versions[versionStr] = unpackModuleVersion
                }
                
                unpackModuleVersion.addArtifacts(resolvedDependency.getModuleArtifacts())
                
                // Recurse down to transitive dependencies.
                traverseResolvedDependencies(
                    packedDependencies, unpackModules, resolvedDependency.getChildren()
                )
            }
        }
    }
    
    public static def getAllUnpackModules(Project project) {
        // Build a list (without duplicates) of all artifacts the project depends on.
        def unpackModules = []
        
        project.configurations.each { conf ->                
            def resConf = conf.resolvedConfiguration
            traverseResolvedDependencies(
                project.packedDependencies, unpackModules, resConf.getFirstLevelModuleDependencies()
            )
        }
        
        unpackModules        
    }
}
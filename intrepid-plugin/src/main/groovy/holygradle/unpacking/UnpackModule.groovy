package holygradle.unpacking

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import holygradle.dependencies.PackedDependencyHandler

class UnpackModule {
    public String group
    public String name
    public Map<String, UnpackModuleVersion> versions = [:]
    
    //used to keep track of what 'brought in' this dependency, to help poor user/debugger see why this instance exists
    public ResolvedDependency dependencyReferencingThis
    
    UnpackModule(String group, String name, ResolvedDependency dependencyReferencingThis) {
        this.group = group
        this.name = name
        this.dependencyReferencingThis = dependencyReferencingThis
    }
        
    public String ToString()
    {
        String result = "UnpackModule: ${this.group}:${this.name}:("
        this.versions.each { versionK, versionV ->
            result += "${versionK};"
        }
        
        result += ")  Parents: {"
        
        ArrayList<String> parents = getParentDependencies(this.dependencyReferencingThis, "")
        parents.each { String p ->
            result += "[${p}]"
        }
        result += "}"
        return result
    }
    
    // Recurse parents and create all the dependency 'paths' as strings
    public ArrayList<String> getParentDependencies(ResolvedDependency d, String depId)
    {
        ArrayList<String> result = new ArrayList<String>()
                    
        ModuleVersionIdentifier v = d.getModule().getId()
        depId = "//${v.getName()}" + depId
        if (d.getParents().isEmpty()) {
            result.add(depId)
        } else {
            d.getParents().each { 
                ResolvedDependency parentDependency ->
                result.addAll(getParentDependencies(parentDependency, depId))
            }
        }
        return result
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
    
    // Get the ivy file for the resolved dependency.  This may either be in the
    // gradle cache, or exist locally in "localArtifacts" (which we create for
    // those who may not have access to the artifact repository)
    private static File getIvyFile(ResolvedDependency resolvedDependency) {
        // Get the first artifact from the set of module artifacts.
        ResolvedArtifact firstArtifact = resolvedDependency.getModuleArtifacts().find { true }
        
        File ivyFile = null
        
        if (firstArtifact == null) {
            // println "Warning: ${resolvedDependency} doesn't have any artifacts. Why are you depending on this configuration?"
        } else {
            // Try to find the ivy file in the gradle cache corresponding to this artifact. This depends upon
            // the structure of the gradle cache, as well as the Ivy format.
            File ivyDirInCache = new File(firstArtifact.getFile().parentFile.parentFile.parentFile, "ivy")
            if (ivyDirInCache.exists()) {
                // Getting the file from the gradle cache.
                ivyDirInCache.traverse {
                    if (it.name.startsWith("ivy-") && it.name.endsWith(".xml")) {
                        ivyFile = it
                    }
                }
            }
            
            // Might we be getting the artifact from a local file-system?  If so then the structure is different from
            // the cache: the zip files for a particular artifact will exist in the same directory as the ivy file.
            // (The localIvyDir for the artifact may not exist if it's a dependency of a sourceDependency which has been
            // fetched in the current run.  We assume it will be present when Gradle is re-run, so just return null for
            // now.)
            File localIvyDir = firstArtifact.getFile().parentFile
            if (localIvyDir.exists()) {
                localIvyDir.traverse {
                    if (it.name.startsWith("ivy-") && it.name.endsWith(".xml")) {
                        ivyFile = it
                    }
                }
            }
        }
        
        return ivyFile
    }
    
    private static void traverseResolvedDependencies(
        String conf,
        Collection<PackedDependencyHandler> packedDependencies,
        Collection<UnpackModule> unpackModules,
        Set<ResolvedDependency> dependencies
    ) {
        dependencies.each { resolvedDependency ->
            ModuleVersionIdentifier moduleVersion = resolvedDependency.getModule().getId()
            String moduleGroup = moduleVersion.getGroup()
            String moduleName = moduleVersion.getName()
            String versionStr = moduleVersion.getVersion()
            
            println "traversing dependency ${moduleName}:${versionStr}"

            // Is there an ivy file corresponding to this dependency? 
            File ivyFile = getIvyFile(resolvedDependency)
            if (ivyFile != null && ivyFile.exists()) {
                // If we found an ivy file then just assume that we should be unpacking it. 
                // Ideally we should look for a custom XML tag that indicates that it was published
                // by the intrepid plugin. However, that would break compatibility with lots of 
                // artifacts that have already been published.
               
                // Find or create an UnpackModule instance.
                UnpackModule unpackModule = unpackModules.find { it.matches(moduleVersion) }
                if (unpackModule == null) {
                    unpackModule = new UnpackModule(moduleGroup, moduleName, resolvedDependency)
                    println "   NEW ${unpackModule.ToString()}"
                    unpackModules << unpackModule
                }
                
                // Find a parent UnpackModuleVersion instance i.e. one which has a dependency on 
                // 'this' UnpackModuleVersion. There will only be a parent if this is a transitive
                // dependency. TODO: There could be more than one parent. Deal with it gracefully.
                UnpackModuleVersion parentUnpackModuleVersion = null
                resolvedDependency.getParents().each { parentDependency ->
                    ModuleVersionIdentifier parentDependencyVersion = parentDependency.getModule().getId()
                    UnpackModule parentUnpackModule = unpackModules.find { it.matches(parentDependencyVersion) }
                    if (parentUnpackModule != null) {
                        parentUnpackModuleVersion = parentUnpackModule.getVersion(parentDependencyVersion)
                    }
                }
                
                // Find or create an UnpackModuleVersion instance.
                UnpackModuleVersion unpackModuleVersion
                if (unpackModule.versions.containsKey(versionStr)) {
                    unpackModuleVersion = unpackModule.versions[versionStr]
                } else {
                
                    // If this resolved dependency is a transitive dependency, "thisPackedDep"
                    // below will be null
                    PackedDependencyHandler thisPackedDep = packedDependencies.find {
                        it.getDependencyName() == moduleName
                    }
                
                    unpackModuleVersion = new UnpackModuleVersion(moduleVersion, ivyFile, parentUnpackModuleVersion, thisPackedDep)
                    unpackModule.versions[versionStr] = unpackModuleVersion
                    println "   CHG ${unpackModule.ToString()}"
                }
                
                unpackModuleVersion.addArtifacts(resolvedDependency.getModuleArtifacts(), conf)
                
                
                // Recurse down to transitive dependencies.
                traverseResolvedDependencies(
                    conf, packedDependencies, unpackModules, resolvedDependency.getChildren()
                )
            }
        }
    }
    
    public static Collection<UnpackModule> getAllUnpackModules(Project project) {
        Collection<UnpackModule> unpackModules = project.unpackModules as Collection<UnpackModule>
        
        if (unpackModules == null) {
            final packedDependencies = project.packedDependencies as Collection<PackedDependencyHandler>

            // Build a list (without duplicates) of all artifacts the project depends on.
            unpackModules = []
            project.configurations.each { conf ->
                ResolvedConfiguration resConf = conf.resolvedConfiguration
                
                traverseResolvedDependencies(
                    conf.name,
                    packedDependencies,
                    unpackModules,
                    resConf.getFirstLevelModuleDependencies()
                )
            }
            
            // Check if we have artifacts for each entry in packedDependency.
            if (!project.gradle.startParameter.isOffline()) {
                packedDependencies.each { dep ->
                    if (!unpackModules.any { it.name == dep.getDependencyName() }) {
                        throw new RuntimeException(
                            "No artifacts detected for dependency '${dep.name}'. " +
                            "Check that you have correctly defined the configurations."
                        )
                    }
                }
            }
            
            // Check if we need to force the version number to be included in the path in order to prevent
            // two different versions of a module to be unpacked to the same location.
            Map<File, Collection<UnpackModuleVersion>> targetLocations = [:]
            
            unpackModules.each { UnpackModule module ->
                module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                    File targetPath = versionInfo.getTargetPathInWorkspace(project).getCanonicalFile()
                    
                    // If something's already targeting this path, make sure its the same module & version!
                    
                    if (targetLocations.containsKey(targetPath)) {
                        
						//SCBR: This is flawed - fix this, it doesn't handle case of different versions
                        if (!targetLocations[targetPath].equals(versionInfo))
                        {
                            // There's a conflict!  Report it to user
                            UnpackModule conflictingUnpackModule = unpackModules.find { it.matches(versionInfo.moduleVersion) }
                            if (conflictingUnpackModule == null) {                                                        
                                throw new RuntimeException(
                                    "Conflicting modules/versions are targetting the same physical location '${targetPath}':\n" +    
                                    "  ${module.ToString()}, and\n" +
                                    "  ${conflictingUnpackModule.ToString()}"                                
                                )
                            } else {
                                // This can only happen if the conflict is caused by something other than an unpackModule
                                throw new RuntimeException(
                                    "Conflicting modules/versions are targetting the same physical location '${targetPath}':\n" +    
                                    "  ${module.ToString()}, clashes with an existing dependency:\n" +
                                    "  ${targetLocations[targetPath].getFullCoordinate()}"
                                )                            
                            }
                        } else {
                            println "INFO: At least two modules are requesting for the same dependency at same location, version is equal so this is ok"
                        }
                    } else {
                        targetLocations[targetPath] = versionInfo
                    }
                }
                
                // More than one version of a module is allowed by gradle, provided that they are different 'configurations'
                // of each version.  e.g., a 'debug' configuration version 0.2 along with a 'release' configuration version '0.3' is ok,
                // but gradle would throw if 'debug' versions 0.2 AND 0.3 were brought in via the dependency tree
                
                // So given this is a permitted scenario, we need to be careful how/where the dependency gets unpacked.
                
                if (module.versions.size() > 1) {
                    int noIncludesCount = 0
                    module.versions.any { String versionStr, UnpackModuleVersion versionInfo ->
                        !versionInfo.includeVersionNumberInPath
                    }
                    if (noIncludesCount > 0) {
                        print "Dependencies have been detected on different versions of the module '${module.name}'. "
                        print "To prevent different versions of this module being unpacked to the same location, the version number will be " 
                        print "appended to the path as '${module.name}-<version>'. You can make this warning disappear by changing the locations " 
                        print "to which these dependencies are being unpacked. "
                        println "For your information, here are the details of the affected dependencies:"
                        module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                            print "  ${module.group}:${module.name}:${versionStr} : " + versionInfo.getIncludeInfo() + " -> "
                            versionInfo.includeVersionNumberInPath = true
                            println versionInfo.getIncludeInfo()
                        }
                    }
                }
            }

            // Check if any target locations are used by more than one module/version.
            targetLocations.each { File target, Collection<String> coordinates ->
                if (coordinates.size() > 1) {
                    throw new RuntimeException(
                        "Multiple different modules/versions are targetting the same location. " +
                        "'${target}' is being targetted by: ${coordinates}. That's not going to work."
                    )
                }
            }
            
            project.ext.unpackModules = unpackModules
        }
        
        unpackModules
    }
}
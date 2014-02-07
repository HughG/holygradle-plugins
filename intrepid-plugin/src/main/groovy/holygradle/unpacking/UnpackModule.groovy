package holygradle.unpacking

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.Task
import holygradle.dependencies.PackedDependencyHandler

/**
 * UnpackModule and UnpackModuleVersion classes are used after script parsing
 * to convert the declared 'packedDependencies', and any transitive dependencies
 * brought in via ivy files, into a set of gradle tasks which carry out the
 * unpacking of artifacts and generation of symlinks where required.
 *
 * The main entry point for this functionality is the static method 'getAllUnpackModules()',
 * called by the main IntrepidPlugin class (within a project.gradle.projectsEvaluated closure).
 *
 * getAllUnpackModules() method populates a collection of UnpackModule/UnpackModuleVersion instances,
 *
 * e.g. consider the tree of dependencies:
 *
 *  + root gradle project
 *      + packedDependency "mylib:0.0.999"
 *          + transitiveDependency "anotherlib:0.0.222" (via ivy.xml)
 *      + packedDependency "anotherlib:0.0.333"
 *
 * this would become the following UnpackModule/UnpackModuleVersions instances:
 *
 *  UnpackModule com.example-corp:mylib with:
 *     UnpackModuleVersion com.example-corp:mylib:0.0.999
 *
 * UnpackModule com.example-corp:anotherlib with:
 *     UnpackModuleVersion com.example-corp:anotherlib:0.0.333
 *     UnpackModuleVersion com.example-corp:anotherlib:0.0.222 required by { com.example-corp:mylib:0.0.999  }

 * As illustrated above an UnpackModule may contain multiple UnpackModuleVersions,
 * represnting 'anotherlib:0.0.333' and 'anotherlib:0.0.222'.  It is possible for a project
 * to have dependencies on different versions of a module provided that the different versions do not
 * occur within the same project configuration.
 *
 */
class UnpackModule {
    public String group
    public String name

    public Map<String, UnpackModuleVersion> versions = [:]
    
    UnpackModule(String group, String name) {
        this.group = group
        this.name = name        
    }
        
    /*
     * ToString
     * Provides user-friendly information about the module being unpacked (and its specific versions).
     * This is used to report more meaningful error information as well as useful for general debugging
     */
    public String ToString()
    {
        String result = "${this.group}:${this.name}\n"
        this.versions.each { versionK, versionV ->
            result += "  ${versionK} -> UnpackModuleVersion ${versionV.ToString()}\n"
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
    
    /**
     * traverseResolvedDependencies
     *
     * static method which generates the instances of UnpackModule/UnpackModuleVersions which in turn create
     * the necessary tasks for unpacking dependencies and creating symlinks.
     * 
     * @param configurationName  The configuration we are collecting 'unpackModules' for
     * @param dependency // The native gradle 'ResolvedDependency' node that we are currently traversing
     * @param packedDependencies, // Collection of the packedDependencies as defined the users' gradle script
     * @param unpackModules (Output) the set of 'unpackModule' object's we're building up during dependency traversal
     *
     **/
    private static void traverseResolvedDependencies(
        String configurationName,
        ResolvedDependency resolvedDependency,
        ResolvedDependency parentResolvedDependency00,
        Collection<PackedDependencyHandler> packedDependencies,
        Collection<UnpackModule> unpackModules        
    ) {
                
        ModuleVersionIdentifier moduleVersion = resolvedDependency.getModule().getId()
        String moduleGroup = moduleVersion.getGroup()
        String moduleName = moduleVersion.getName()
        String versionStr = moduleVersion.getVersion()
        
        //print "traversing dependency ${moduleName}:${versionStr}"
        //if (parentResolvedDependency00 == null) {
        //    println " which is 1st level"
        //} else {
        //    ModuleVersionIdentifier parentVersion = parentResolvedDependency00.getModule().getId()
        //    println " with parent ${parentVersion.getName()}:${parentVersion.getVersion()}"
        //}

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
                unpackModule = new UnpackModule(moduleGroup, moduleName)
                unpackModules << unpackModule
                // println "Created new UnpackModule: ${unpackModule.ToString()}"
            }
                               
            // Find or create an UnpackModuleVersion instance.
            UnpackModuleVersion unpackModuleVersion
            if (unpackModule.versions.containsKey(versionStr)) {
                unpackModuleVersion = unpackModule.versions[versionStr]
            } else {
            
                // If this resolved dependency is a transitive dependency, "thisPackedDep"
                // below will be null, unless of course it is both a packedDependency AND a transitive dependency
                // of another module!
                PackedDependencyHandler thisPackedDep = packedDependencies.find {
                    (it.getDependencyName() == moduleName) && (it.getVersionStr() == versionStr)
                }
            
                unpackModuleVersion = new UnpackModuleVersion(moduleVersion, ivyFile, thisPackedDep)
                unpackModule.versions[versionStr] = unpackModuleVersion
                // println "Added version to UnpackModule: ${unpackModule.ToString()}"
            }
                
            // Search for a parent UnpackModuleVersion instance i.e. one which has a dependency on 
            // 'this' UnpackModuleVersion. There will only be a parent if this is a transitive
            // dependency, and there may be more than one 
            // (e.g., then if A->B, B->C, A->C, where -> means "depends on", C has two parents {A,B} )
            if (parentResolvedDependency00 != null)
            {
                ModuleVersionIdentifier parentDependencyVersion = parentResolvedDependency00.getModule().getId()
                UnpackModule parentUnpackModule = unpackModules.find { it.matches(parentDependencyVersion) }
                UnpackModuleVersion parentUnpackModuleVersion = null
                if (parentUnpackModule != null) {
                    // Make sure this instance is aware of ALL its parents - we use such information
                    // to build up the 'paths' where symlinks should be (or where the module should be unpacked
                    // if not going to the cache)
                    parentUnpackModuleVersion = parentUnpackModule.getVersion(parentDependencyVersion)
                    unpackModuleVersion.addParent(parentUnpackModuleVersion)
                    // println "Added parent to UnpackModule: ${unpackModule.ToString()}"
                }                
            }
                
            unpackModuleVersion.addArtifacts(resolvedDependency.getModuleArtifacts(), configurationName)
        }
        
        // Recurse down the tree of transitive dependencies        
        resolvedDependency.getChildren().each { ResolvedDependency childDependency ->
        
            traverseResolvedDependencies(
                configurationName, childDependency, resolvedDependency, packedDependencies, unpackModules
            )
        }
    }
    
    public static Collection<UnpackModule> getAllUnpackModules(Project project) {
        Collection<UnpackModule> unpackModules = project.unpackModules as Collection<UnpackModule>
        
        if (unpackModules == null) {
            final packedDependencies = project.packedDependencies as Collection<PackedDependencyHandler>

            // Build a list (without duplicates) of all artifacts the project depends on.
            unpackModules = []
            project.configurations.each { projectConfiguration ->
                ResolvedConfiguration resolvedConfiguration = projectConfiguration.resolvedConfiguration
            
                // Kick off gradle to generate a tree of dependencies
                Set<ResolvedDependency> firstLevelProjectDependencies = 
                    resolvedConfiguration.getFirstLevelModuleDependencies()

                // Recursive call to traverse the tree of dependencies and identify ones that we need 
                // to genereate unpack/symlink tasks for
                firstLevelProjectDependencies.each { ResolvedDependency firstLevelProjectDependency ->
                    traverseResolvedDependencies(
                        projectConfiguration.name, // Name of the native gradle 'configuration' that we are currently working on
                        firstLevelProjectDependency, // The native gradle 'ResolvedDependency' node that we are currently traversing
                        null, // on this call, ParentDependency is null since this is a firstLevelProjectDependency
                        packedDependencies, // Collection of the packedDependencies as defined the users' gradle script
                        unpackModules // Output: i.e., the set of object's we're building up during dependency traversal
                    )
                }
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
                    Set<File> targetPaths = versionInfo.getTargetPathsInWorkspace(project)
                    
                    // If something's already targeting one of the paths where this module needs to exist, 
                    // then make sure its the same module & version!
                    targetPaths.each { File targetPath ->
                        if (targetLocations.containsKey(targetPath)) {
                            if (!targetLocations[targetPath].contains(versionInfo)) {
                                targetLocations[targetPath].add(versionInfo)
                            }
                        } else {
                            targetLocations[targetPath] = [versionInfo]
                        }
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
            targetLocations.each { File target, Collection<UnpackModuleVersion> unpackModuleVersions ->
                
                if (unpackModuleVersions.size() > 1) {
                    GString msg = "The following dependencies are being targetted to the same location ${project.projectDir.toURI().relativize(target.toURI()).getPath()}:\n"
                    unpackModuleVersions.each { UnpackModuleVersion version ->
                        msg +=  "- ${version.ToString()}\n"
                    }
                    throw new RuntimeException(msg)
                }
            }
            
            project.ext.unpackModules = unpackModules
        }
        
        unpackModules
    }
}
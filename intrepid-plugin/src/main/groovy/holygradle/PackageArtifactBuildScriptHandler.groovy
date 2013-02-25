package holygradle

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.bundling.*

class PackageArtifactBuildScriptHandler {
    private boolean atTop = true
    private def textAtTop = []
    private def pinnedSourceDependencies = []
    private def packedDependencies = [:]
    private def textAtBottom = []
    private def ivyRepositories = []
    private String myCredentialsConfig
    private def symlinkPatterns = []
    private String publishUrl = null
    private String publishCredentials = null
    public boolean generateSettingsFileForSubprojects = true
    public boolean unpackToCache = false
    
    public PackageArtifactBuildScriptHandler() {
    }
    
    public void add(String text) {
        if (atTop) {
            textAtTop.add(text)
        } else {
            textAtBottom.add(text)
        }
    }
    
    public void addIvyRepository(String url) {
        addIvyRepository(url, null)
    }
    
    public void addIvyRepository(String url, String myCredentialsConfig) {
        ivyRepositories.add(url)
        this.myCredentialsConfig = myCredentialsConfig
        atTop = false
    }
    
    public void addPublishPackages(String url, String myCredentialsConfig) {
        publishUrl = url
        publishCredentials = myCredentialsConfig
    }
    
    public void addPinnedSourceDependency(String... sourceDep) {
        for (s in sourceDep) {
            pinnedSourceDependencies.add(s)
        }
        atTop = false
    }
    
    public void addPackedDependency(String packedDepName, String... configurations) {
        packedDependencies[packedDepName] = configurations
        atTop = false
    }
    
    public void includeSymlinks(String... patterns) {
        for (p in patterns) {
            symlinkPatterns.add(p)
        }
    }
    
    public boolean buildScriptRequired() {
        return pinnedSourceDependencies.size() > 0 || packedDependencies.size() > 0
    }
    
    private static List<SourceDependencyHandler> findSourceDependencies(Project project, String sourceDepWildcard) {
        List<SourceDependencyHandler> matches = new LinkedList<SourceDependencyHandler>()
        
        project.subprojects { p ->
            matches.addAll(findSourceDependencies(p, sourceDepWildcard))
        }
        
        def sourceDependencies = project.extensions.findByName("sourceDependencies")
        if (sourceDependencies != null) {
            sourceDependencies.each {
                if (Helper.wildcardMatch(sourceDepWildcard, it.name)) {
                    matches.add(it)
                }
            }
        }
        
        matches
    }
    
    private static List<PackedDependencyHandler> findPackedDependencies(Project project, String packedDepWildcard) {
        List<PackedDependencyHandler> matches = new LinkedList<PackedDependencyHandler>()
        
        project.subprojects { p ->
            matches.addAll(findPackedDependencies(p, packedDepWildcard))
        }
        
        def packedDependencies = project.extensions.findByName("packedDependencies")
        if (packedDependencies != null) {
            packedDependencies.each {
                if (Helper.wildcardMatch(packedDepWildcard, it.name)) {
                    matches.add(it)
                }
            }
        }
        
        matches
    }
    
    private static def collectSourceDependenciesForPinned(Project project, def dependencyWildcards) {
        def allSourceDeps = [:]
        dependencyWildcards.each { wildcard ->
            findSourceDependencies(project.rootProject, wildcard).each { sourceDep ->
                def targetPath = sourceDep.name
                if (allSourceDeps.containsKey(targetPath)) {
                    int curConf = allSourceDeps[targetPath].publishingHandler.configurations.size()
                    int itConf = sourceDep.publishingHandler.configurations.size()
                    if (itConf > curConf) {
                        allSourceDeps[targetPath] = sourceDep
                    }
                } else {
                    allSourceDeps[targetPath] = sourceDep
                }
            }
        }
        allSourceDeps.values()
    }
    
    private static def collectSourceDependenciesForPacked(Project project, def sourceDepNames) {
        def allSourceDeps = [:]
        sourceDepNames.each { sourceDepName ->
            findSourceDependencies(project.rootProject, sourceDepName).each { sourceDep ->
                if (allSourceDeps.containsKey(sourceDepName)) {
                    int curConf = allSourceDeps[sourceDepName].publishingHandler.configurations.size()
                    int itConf = sourceDep.publishingHandler.configurations.size()
                    if (itConf > curConf) {
                        allSourceDeps[sourceDepName] = sourceDep
                    }
                } else {
                    allSourceDeps[sourceDepName] = sourceDep
                }
            }
        }
        allSourceDeps
    }
    
    private static def collectPackedDependencies(Project project, def packedDepNames) {
        def allPackedDeps = [:]
        packedDepNames.each { packedDepName ->
            findPackedDependencies(project.rootProject, packedDepName).each {
                allPackedDeps[packedDepName] = it
            }
        }
        allPackedDeps
    }
    
    private void collectSymlinks(Project project, String sourceDepName, SymlinkHandler allSymlinks) {
        if (Helper.anyWildcardMatch(symlinkPatterns, sourceDepName)) {
            def depProject = project.rootProject.findProject(sourceDepName)
            if (depProject != null) {
                def depSymlinks = depProject.extensions.findByName("symlinks")
                allSymlinks.addFrom(sourceDepName, depSymlinks)
            }
        }
    }
    
    public void createBuildScript(Project project, File buildFile) {
        if (!buildFile.parentFile.exists()) {
            buildFile.parentFile.mkdirs()
        }
    
        StringBuilder buildScript = new StringBuilder()
        
        // Text at the top of the build script
        textAtTop.each {
            buildScript.append(it)
            buildScript.append("\n")
        }
        
        // Include plugins
        buildScript.append("buildscript {\n")
        def pluginUsagesExtension = project.extensions.findByName("pluginUsages")
        ["intrepid", "my-credentials"].each { pluginName ->
            def pluginVersion = project.gplugins.usages[pluginName]
            if (pluginUsagesExtension != null) {
                pluginVersion = pluginUsagesExtension.getVersion(pluginName)
            }
            buildScript.append("    gplugins.use \"${pluginName}:")
            buildScript.append(pluginVersion)
            buildScript.append("\"\n")
        }
        buildScript.append("}\n")
        buildScript.append("gplugins.apply()\n")
        buildScript.append("\n")
        
        // Add repositories
        if (ivyRepositories.size() > 0) {
            buildScript.append("repositories.ivy {\n")
            buildScript.append("    credentials {\n")
            buildScript.append("        username my.username(")
            if (myCredentialsConfig != null) {
                buildScript.append("\"${myCredentialsConfig}\"")
            }
            buildScript.append(")\n")
            buildScript.append("        password my.password(")
            if (myCredentialsConfig != null) {
                buildScript.append("\"${myCredentialsConfig}\"")
            }
            buildScript.append(")\n")
            buildScript.append("    }\n")
            ivyRepositories.each {
                buildScript.append("    url \"")
                buildScript.append(it)
                buildScript.append("\"\n")
            }
            buildScript.append("}\n")
            buildScript.append("\n")
        }
        
        // Collect symlinks for 'this' project.
        SymlinkHandler allSymlinks = new SymlinkHandler()
        def thisSymlinkHandler = project.extensions.findByName("symlinks")
        allSymlinks.addFrom(project.name, thisSymlinkHandler)
        
        // sourceDependencies block
        def pinnedSourceDeps = collectSourceDependenciesForPinned(project, pinnedSourceDependencies)
        if (pinnedSourceDeps.size() > 0) {
            if (!generateSettingsFileForSubprojects) {
                buildScript.append("fetchAllDependencies {\n")
                buildScript.append("    generateSettingsFileForSubprojects = false\n")
                buildScript.append("}\n")
                buildScript.append("\n")
            }
            
            buildScript.append("sourceDependencies {\n")
            for (sourceDep in pinnedSourceDeps) {
                def repo = SourceControlRepositories.get(sourceDep.getAbolutePath())
                if (repo != null) {
                    buildScript.append(" "*4)
                    buildScript.append("\"")
                    buildScript.append(sourceDep.getFullTargetPathRelativeToRootProject()) 
                    buildScript.append("\" {\n")
                    buildScript.append(" "*8)
                    buildScript.append(repo.getProtocol())
                    buildScript.append(" \"")
                    buildScript.append(repo.getUrl().replace("\\", "/"))
                    buildScript.append("@")
                    buildScript.append(repo.getRevision())
                    buildScript.append("\"\n")
                    buildScript.append(" "*4)
                    buildScript.append("}\n")
                }
                
                collectSymlinks(project, sourceDep.getTargetName(), allSymlinks)
            }
            buildScript.append("}\n")
        }
        
        if (packedDependencies.size() > 0) {
            def allConfs = []
            packedDependencies.each { depName, depConfs ->
                for (c in depConfs) {
                    Helper.processConfiguration(c, allConfs, "Formatting error for '$depName' in 'addPackedDependency'.")
                }
            }
            def confSet = new HashSet<String>()
            for (c in allConfs) {
                confSet.add(c[0])
            }
            
            buildScript.append("configurations {\n")
            confSet.sort().each {
                buildScript.append("    ")
                if (it == "default") {
                    buildScript.append("it.\"default\"")
                } else {
                    buildScript.append(it)
                }
                buildScript.append("\n")
            }
            buildScript.append("}\n")
            buildScript.append("\n")
        }
        
        if (!unpackToCache) {
            buildScript.append("packedDependenciesDefault {\n")
            buildScript.append("    unpackToCache = false\n")
            buildScript.append("}")
            buildScript.append("\n")
        }
        
        if (packedDependencies.size() > 0) {        
            buildScript.append("packedDependencies {\n")
            def sourceDeps = collectSourceDependenciesForPacked(project, packedDependencies.keySet())
            for (sourceDepName in sourceDeps.keySet()) {
                def sourceDep = sourceDeps[sourceDepName]
                writePackedDependency(
                    buildScript,
                    sourceDep.getFullTargetPathRelativeToRootProject(),
                    sourceDep.getLatestPublishedDependencyCoordinate(project),
                    packedDependencies[sourceDepName]
                )
                
                collectSymlinks(project, sourceDep.getTargetName(), allSymlinks)
            }
            def packedDeps = collectPackedDependencies(project, packedDependencies.keySet())
            for (packedDepName in packedDeps.keySet()) {
                def packedDep = packedDeps[packedDepName]
                writePackedDependency(
                    buildScript, 
                    packedDep.getFullTargetPathRelativeToRootProject(), 
                    packedDep.getDependencyCoordinate(project),
                    packedDependencies[packedDepName]
                )
            }
            // Some packed dependencies will explicitly specify the full coordinate, so just
            // publish them as-is.
            packedDependencies.each { packedDepName, packedDepConfigs ->
                def groupMatch = packedDepName =~ /.+:(.+):.+/
                if (groupMatch.size() > 0) {
                    writePackedDependency(buildScript, groupMatch[0][1], packedDepName, packedDepConfigs)
                }
            }
            buildScript.append("}\n")
        }
        buildScript.append("\n")
        
        // The 'symlinks' block.
        allSymlinks.writeScript(buildScript)
        
        // Generate the 'publishPackages' block:
        if (publishUrl != null && publishCredentials != null) {
            buildScript.append('publishPackages {\n')
            buildScript.append('    group "')
            buildScript.append(project.group)
            buildScript.append('"\n')
            buildScript.append('    nextVersionNumber "')
            buildScript.append(project.version)
            buildScript.append('"\n')
            buildScript.append('    repositories.ivy {\n')
            buildScript.append('        credentials {\n')
            buildScript.append('            username my.username("')
            buildScript.append(publishCredentials)
            buildScript.append('")\n')
            buildScript.append('            password my.password("')
            buildScript.append(publishCredentials)
            buildScript.append('")\n')
            buildScript.append('        }\n')
            buildScript.append('        url "')
            buildScript.append(publishUrl)
            buildScript.append('"\n')
            buildScript.append('    }\n')
            buildScript.append('}\n')
            buildScript.append('\n')
        }
        
        // Text at the bottom of the build script
        textAtBottom.each {
            buildScript.append(it)
            buildScript.append("\n")
        }
        
        buildFile.write(buildScript.toString())
    }
    
    private static void writePackedDependency(StringBuilder buildScript, String name, String fullCoordinate, def configs) {
        buildScript.append("    \"")
        buildScript.append(name)
        buildScript.append("\" {\n")
        buildScript.append("        dependency \"")
        buildScript.append(fullCoordinate)
        buildScript.append("\"\n")
        buildScript.append("        configuration ")
        def quoted = configs.collect { "\"${it}\"" }
        buildScript.append(quoted.join(", "))
        buildScript.append("\n")
        buildScript.append("    }\n")
    }
}
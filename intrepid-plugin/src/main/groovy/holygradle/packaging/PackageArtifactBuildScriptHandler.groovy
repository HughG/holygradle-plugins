package holygradle.packaging

import holygradle.custom_gradle.PluginUsages
import holygradle.custom_gradle.util.Wildcard
import holygradle.dependencies.PackedDependencyHandler
import holygradle.publishing.RepublishHandler
import holygradle.scm.SourceControlRepository
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.symlinks.SymlinkHandler
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import holygradle.Helper
import holygradle.scm.SourceControlRepositories

import java.util.regex.Matcher

class PackageArtifactBuildScriptHandler {
    private boolean atTop = true
    private Collection<String> textAtTop = []
    private Collection<String> pinnedSourceDependencies = []
    private Map<String, Collection<String>> packedDependencies = [:]
    private Collection<String> textAtBottom = []
    private Collection<String> ivyRepositories = []
    private RepublishHandler republishHandler = null
    private String myCredentialsConfig
    private List<String> symlinkPatterns = []
    private String publishUrl = null
    private String publishCredentials = null
    public boolean generateSettingsFileForSubprojects = true
    public boolean unpackToCache = false
    public boolean createPackedDependenciesSettingsFile = false
    
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
        pinnedSourceDependencies.addAll(sourceDep)
        atTop = false
    }
    
    public void addPackedDependency(String packedDepName, String... configurations) {
        packedDependencies[packedDepName] = configurations as Collection<String>
        atTop = false
    }
    
    public void addRepublishing(Closure closure) {
        if (republishHandler == null) {
            republishHandler = new RepublishHandler()
        }
        ConfigureUtil.configure(closure, republishHandler)
    }
    
    public void includeSymlinks(String... patterns) {
        for (p in patterns) {
            symlinkPatterns.add(p)
        }
    }
    
    public boolean buildScriptRequired() {
        return pinnedSourceDependencies.size() > 0 || packedDependencies.size() > 0
    }

    private static List<SourceDependencyHandler> findSourceDependencies(
            Project project,
            String sourceDepNameToFind,
            boolean allowWildcard)
    {
        List<SourceDependencyHandler> matches = new LinkedList<SourceDependencyHandler>()
        
        project.subprojects { Project p ->
            matches.addAll(findSourceDependencies(p, sourceDepNameToFind, allowWildcard))
        }
        
        Collection<SourceDependencyHandler> sourceDependencies =
            project.extensions.findByName("sourceDependencies") as Collection<SourceDependencyHandler>
        if (sourceDependencies != null) {
            sourceDependencies.each {
                // Each sourceDependency name is actually a path - here we are only interested in matching the
                // last entity of the path with the name provided in the user's "addSourceDependency"
                Matcher folderNameUsingRegex = it.name =~ /([^\\/]+)$/

                if (!folderNameUsingRegex.find()) {
                    project.logger.warn "Failed to parse dependency name from path ${it.name}"
                } else {
                    if (allowWildcard) {
                        if (Wildcard.match(sourceDepNameToFind, folderNameUsingRegex.group(1))) {
                            matches.add(it)
                        }
                    } else if (folderNameUsingRegex.group(1) == sourceDepNameToFind) {
                        matches.add(it)
                    }
                }
            }
        }
        return matches
    }
    
    private static List<PackedDependencyHandler> findPackedDependencies(Project project, String packedDepName) {
        List<PackedDependencyHandler> matches = new LinkedList<PackedDependencyHandler>()
        
        project.subprojects { Project p ->
            matches.addAll(findPackedDependencies(p, packedDepName))
        }

        Collection<PackedDependencyHandler> packedDependencies =
            project.extensions.findByName("packedDependencies") as Collection<PackedDependencyHandler>
        if (packedDependencies != null) {
            packedDependencies.each {
                // Each packedDependency name is actually a path - here we are only interested in matching the
                // last entity of the path with the name provided in the user's "addPackedDependency"
                Matcher folderNameUsingRegex = it.name =~ /([^\\/]+)$/

                if (!folderNameUsingRegex.find()) {
                    project.logger.warn "Failed to parse dependency name from path ${it.name}"
                } else {
                    if (folderNameUsingRegex.group(1) == packedDepName) {
                        matches.add(it)
                    }
                }
            }
        }
        return matches
    }
    

    private static Map<String, SourceDependencyHandler> collectSourceDependenciesForPacked(
        Project project,
        Iterable<String> sourceDepNames
    ) {
        Map<String, SourceDependencyHandler> allSourceDeps = [:]
        sourceDepNames.each { String sourceDepName ->
            findSourceDependencies(project.rootProject, sourceDepName, /*allowWildcard=*/false ).each { SourceDependencyHandler sourceDep ->
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

    private static Collection<SourceDependencyHandler> collectSourceDependenciesForPinned(
        Project project,
        Iterable<String> dependencyWildcards
    ) {
        Map<String, SourceDependencyHandler> allSourceDeps = [:]
        dependencyWildcards.each { String wildcard ->
            findSourceDependencies(project.rootProject, wildcard, /*allowWildcard=*/true).each { SourceDependencyHandler sourceDep ->
                String targetPath = sourceDep.name
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

    private static Map<String, PackedDependencyHandler> collectPackedDependencies(
        Project project,
        Collection<String> packedDepNames
    ) {
        Map<String, PackedDependencyHandler> allPackedDeps = [:]
        packedDepNames.each { String packedDepName ->
            findPackedDependencies(project.rootProject, packedDepName).each {
                allPackedDeps[packedDepName] = it
            }
        }
        allPackedDeps
    }
    
    private void collectSymlinks(Project project, String sourceDepName, SymlinkHandler allSymlinks) {
        if (Wildcard.anyMatch(symlinkPatterns, sourceDepName)) {
            Project depProject = project.rootProject.findProject(sourceDepName)
            if (depProject != null) {
                SymlinkHandler depSymlinks = depProject.extensions.findByName("symlinks") as SymlinkHandler
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
        PluginUsages pluginUsagesExtension = project.extensions.findByName("pluginUsages") as PluginUsages
        ["intrepid", "devenv", "my-credentials"].each { String pluginName ->
            String pluginVersion = project.gplugins.usages[pluginName] as String
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
        if (!ivyRepositories.empty) {
            buildScript.append("repositories {\n")
            ivyRepositories.each { repo ->
                buildScript.append("    ivy {\n")
                buildScript.append("        credentials {\n")
                buildScript.append("            username my.username(")
                if (myCredentialsConfig != null) {
                    buildScript.append("\"${myCredentialsConfig}\"")
                }
                buildScript.append(")\n")
                buildScript.append("            password my.password(")
                if (myCredentialsConfig != null) {
                    buildScript.append("\"${myCredentialsConfig}\"")
                }
                buildScript.append(")\n")
                buildScript.append("        }\n")
                buildScript.append("        url \"")
                buildScript.append(repo)
                buildScript.append("\"\n")
                buildScript.append("    }\n")
            }
            buildScript.append("}\n")
            buildScript.append("\n")
        }
        
        // Collect symlinks for 'this' project.
        SymlinkHandler allSymlinks = new SymlinkHandler()
        SymlinkHandler thisSymlinkHandler = project.extensions.findByName("symlinks") as SymlinkHandler
        allSymlinks.addFrom(project.name, thisSymlinkHandler)
        
        // sourceDependencies block
        Collection<SourceDependencyHandler> pinnedSourceDeps =
            collectSourceDependenciesForPinned(project, pinnedSourceDependencies)
        if (pinnedSourceDeps.size() > 0) {
            if (!generateSettingsFileForSubprojects) {
                buildScript.append("fetchAllDependencies {\n")
                buildScript.append("    generateSettingsFileForSubprojects = false\n")
                buildScript.append("}\n")
                buildScript.append("\n")
            }
            
            buildScript.append("sourceDependencies {\n")
            for (SourceDependencyHandler sourceDep in pinnedSourceDeps) {
                // In this case, we create a new SourceControlRepository instead of trying to get the "sourceControl"
                // extension from the sourceDep.project, because that project itself may not have the intrepid plugin
                // applied, in which case it won't have that extension.  We need to get the SourceControlRepository
                // object, instead of just using the SourceDependencyHandler, to get the actual revision.
                SourceControlRepository repo = SourceControlRepositories.get(project.rootProject, sourceDep.absolutePath)
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
                
                collectSymlinks(project, sourceDep.targetName, allSymlinks)
            }
            buildScript.append("}\n")
        }
        
        if (packedDependencies.size() > 0) {
            Collection<AbstractMap.SimpleEntry<String, String>> allConfs = []
            packedDependencies.each { depName, depConfs ->
                for (c in depConfs) {
                    Helper.parseConfigurationMapping(c, allConfs, "Formatting error for '$depName' in 'addPackedDependency'.")
                }
            }
            Set<String> confSet = new HashSet<String>()
            for (c in allConfs) {
                confSet.add(c.key)
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
        
        if (!unpackToCache || createPackedDependenciesSettingsFile) {
            buildScript.append("packedDependenciesDefault {\n")
            if (!unpackToCache) buildScript.append("    unpackToCache = false\n")
            if (createPackedDependenciesSettingsFile) buildScript.append("    createSettingsFile = true\n")
            buildScript.append("}\n")
            buildScript.append("\n")
        }
        
        if (packedDependencies.size() > 0) {
            buildScript.append("packedDependencies {\n")
            Map<String, SourceDependencyHandler> sourceDeps = collectSourceDependenciesForPacked(project, packedDependencies.keySet())
            for (sourceDepName in sourceDeps.keySet()) {
                SourceDependencyHandler sourceDep = sourceDeps[sourceDepName]
                writePackedDependency(
                    buildScript,
                    sourceDep.getFullTargetPathRelativeToRootProject(),
                    sourceDep.getLatestPublishedDependencyCoordinate(project),
                    packedDependencies[sourceDepName]
                )
                
                collectSymlinks(project, sourceDep.targetName, allSymlinks)
            }
            Map<String, PackedDependencyHandler> packedDeps = collectPackedDependencies(project, packedDependencies.keySet())
            for (packedDepName in packedDeps.keySet()) {
                PackedDependencyHandler packedDep = packedDeps[packedDepName]
                writePackedDependency(
                    buildScript, 
                    packedDep.getFullTargetPathRelativeToRootProject(), 
                    packedDep.getDependencyCoordinate(),
                    packedDependencies[packedDepName]
                )
            }
            // Some packed dependencies will explicitly specify the full coordinate, so just
            // publish them as-is.
            packedDependencies.each { String packedDepName, Collection<String> packedDepConfigs ->
                Matcher groupMatch = packedDepName =~ /.+:(.+):.+/
                if (groupMatch.size() > 0) {
                    final List<String> match = (List<String>) groupMatch[0]
                    writePackedDependency(buildScript, match[1], packedDepName, packedDepConfigs)
                }
            }
            buildScript.append("}\n")
        }
        buildScript.append("\n")
        
        // The 'symlinks' block.
        allSymlinks.writeScript(buildScript)
        
        // Generate the 'publishPackages' block:
        boolean openPublishPackages = (publishUrl != null && publishCredentials != null) || republishHandler != null
        
        if (openPublishPackages) {
            buildScript.append('publishPackages {\n')
            if (publishUrl != null && publishCredentials != null) {
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
            }
            if (republishHandler != null) {
                republishHandler.writeScript(buildScript, 4)
            }
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
    
    private static void writePackedDependency(
        StringBuilder buildScript,
        String name,
        String fullCoordinate,
        Collection<String> configs
    ) {
        buildScript.append("    \"")
        buildScript.append(name)
        buildScript.append("\" {\n")
        buildScript.append("        dependency \"")
        buildScript.append(fullCoordinate)
        buildScript.append("\"\n")
        buildScript.append("        configuration ")
        List<GString> quoted = configs.collect { "\"${it}\"" }
        buildScript.append(quoted.join(", "))
        buildScript.append("\n")
        buildScript.append("    }\n")
    }
}
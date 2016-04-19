package holygradle.packaging

import holygradle.custom_gradle.PluginUsages
import holygradle.dependencies.DependencyHandler
import holygradle.dependencies.PackedDependencyHandler
import holygradle.io.FileHelper
import holygradle.links.LinkHandler
import holygradle.publishing.RepublishHandler
import holygradle.scm.SourceControlRepository
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import holygradle.Helper
import holygradle.scm.SourceControlRepositories

import java.util.regex.Matcher

class PackageArtifactBuildScriptHandler implements PackageArtifactTextFileHandler {
    private boolean atTop = true
    private Collection<String> textAtTop = []
    private Collection<String> pinnedSourceDependencies = new HashSet<String>()
    private Map<String, Collection<String>> packedDependencies = [:]
    private Collection<String> textAtBottom = []
    private Collection<String> ivyRepositories = []
    private RepublishHandler republishHandler = null
    private String myCredentialsConfig
    private String publishUrl = null
    private String publishCredentials = null
    public boolean generateSettingsFileForSubprojects = true
    public boolean unpackToCache = false
    public boolean createPackedDependenciesSettingsFile = false
    private final Project project

    public PackageArtifactBuildScriptHandler(Project project) {
        this.project = project
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

    public void addPinnedSourceDependency(SourceDependencyHandler... sourceDep) {
        //noinspection GroovyAssignabilityCheck
        addPinnedSourceDependency(*(sourceDep*.targetName))
        atTop = false
    }

    public void addPackedDependency(String packedDepName, String... configurations) {
        if (configurations.length == 0) {
            throw new RuntimeException(
                "Packed dependency ${packedDepName} was added with no configurations; need at least one."
            )
        }
        if (packedDependencies.containsKey(packedDepName)) {
            throw new RuntimeException("Packed dependency ${packedDepName} has already been added.")
        }
        packedDependencies[packedDepName] = configurations as Collection<String>
        atTop = false
    }

    public void addPackedDependency(DependencyHandler dep, String... configurations) {
        //noinspection GroovyAssignabilityCheck
        addPackedDependency(*(dep*.targetName), configurations)
        atTop = false
    }

    public void addRepublishing(Closure closure) {
        if (republishHandler == null) {
            republishHandler = new RepublishHandler()
        }
        ConfigureUtil.configure(closure, republishHandler)
    }

    public boolean buildScriptRequired() {
        return ([
            textAtTop,
            textAtBottom,
            pinnedSourceDependencies,
            ivyRepositories
        ] as Collection[]).any { !it.empty } ||
            !packedDependencies.empty ||
            publishInfoSpecified()
    }

    private static <T extends DependencyHandler> void findDependencies(
        Project project,
        String depNameToFind,
        String depsExtensionName,
        Collection<T> matches
    ) {
        project.subprojects { Project p ->
            findDependencies(p, depNameToFind, depsExtensionName, matches)
        }

        Collection<T> dependencies = project.extensions.findByName(depsExtensionName) as Collection<T>
        if (dependencies != null) {
            matches.addAll(dependencies.findAll { it.targetName == depNameToFind })
        }
    }

    private static List<SourceDependencyHandler> findSourceDependencies(Project project, String sourceDepName) {
        List<SourceDependencyHandler> matches = new LinkedList<SourceDependencyHandler>()
        findDependencies(project, sourceDepName, "sourceDependencies", matches)
        return matches
    }
    
    private static List<PackedDependencyHandler> findPackedDependencies(Project project, String packedDepName) {
        List<PackedDependencyHandler> matches = new LinkedList<PackedDependencyHandler>()
        findDependencies(project, packedDepName, "packedDependencies", matches)
        return matches
    }
    

    private static Map<String, SourceDependencyHandler> collectSourceDependencies(
        Project project,
        boolean throwIfAnyMissing,
        Iterable<String> sourceDepNames
    ) {
        Map<String, List<SourceDependencyHandler>> allSourceDeps = [:].withDefault { new ArrayList<>() }
        sourceDepNames.each { String sourceDepName ->
            findSourceDependencies(project.rootProject, sourceDepName).each { SourceDependencyHandler sourceDep ->
                allSourceDeps[sourceDepName] << sourceDep
            }
        }
        boolean sourceDependencyPathsClash = false
        allSourceDeps.each { sourceDepName, List<SourceDependencyHandler> handlersMatchingName ->
            Map<String, List<SourceDependencyHandler>> handlersByPath =
                handlersMatchingName.groupBy { it.fullTargetPathRelativeToRootProject }
            if (handlersByPath.size() > 1) {
                sourceDependencyPathsClash = true
                project.logger.error(
                    "The name '${sourceDepName}' is used by source dependencies targetting more than one path:"
                )
                handlersByPath.each { path, handlers ->
                    project.logger.error("  path '${new File(project.rootProject.projectDir, path)}' is targetted by")
                    handlers.each { handler ->
                        project.logger.error("    source dependency '${handler.name}' in ${handler.project}")
                    }
                }
            }
        }
        if (sourceDependencyPathsClash) {
            throw new RuntimeException("Failed to find a unique target path for some source dependency names")
        }
        if (throwIfAnyMissing) {
            Set<String> wantedSourceDepNames = new TreeSet<String>()
            sourceDepNames.each { wantedSourceDepNames.add(it) }
            Set<String> foundSourceDepNames = new TreeSet<String>(allSourceDeps.keySet())
            Set<String> missingSourceDepNames = wantedSourceDepNames - foundSourceDepNames
            if (!missingSourceDepNames.empty) {
                throw new RuntimeException(
                    "Looking for source dependencies ${wantedSourceDepNames}, failed to find ${missingSourceDepNames}"
                )
            }
        }
        // We may have more than one handler for each name, but we know they all point to the same path, so any will do.
        allSourceDeps.collectEntries { name, handlers -> [name, handlers[0]] }
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

    @Override
    String getName() {
        return "build.gradle"
    }

    @Override
    public void writeFile(File buildFile) {
        FileHelper.ensureMkdirs(buildFile.parentFile, "as output folder for build script ${buildFile}")
    
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
        
        // Collect links for 'this' project.
        LinkHandler allLinks = new LinkHandler()
        LinkHandler thisLinkHandler = project.extensions.findByName("links") as LinkHandler
        allLinks.addFrom(project.name, thisLinkHandler)
        
        // sourceDependencies block
        Collection<SourceDependencyHandler> pinnedSourceDeps =
            collectSourceDependencies(project, true, pinnedSourceDependencies).values()
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
                // applied, in which case it won't have that extension.  We need to create the SourceControlRepository
                // object, instead of just using the SourceDependencyHandler, to create the actual revision.
                SourceControlRepository repo = SourceControlRepositories.create(project.rootProject, sourceDep.absolutePath)
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
            Set<String> wantedPackedDepNames = new TreeSet<String>(packedDependencies.keySet())
            Set<String> missingPackedDepNames = new TreeSet<String>(packedDependencies.keySet())

            buildScript.append("packedDependencies {\n")
            // The user may want to treat a build-time source dependency as a use-time packed dependency, so first check
            // the source dependencies.
            Map<String, SourceDependencyHandler> sourceDeps = collectSourceDependencies(project, false, packedDependencies.keySet())
            for (sourceDepName in sourceDeps.keySet()) {
                SourceDependencyHandler sourceDep = sourceDeps[sourceDepName]
                writePackedDependency(
                    buildScript,
                    sourceDep.getFullTargetPathRelativeToRootProject(),
                    sourceDep.getDependencyCoordinate(),
                    packedDependencies[sourceDepName]
                )
            }
            missingPackedDepNames.removeAll(sourceDeps.keySet())
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
            missingPackedDepNames.removeAll(packedDeps.keySet())
            // Some packed dependencies will explicitly specify the full coordinate, so just
            // publish them as-is.
            packedDependencies.each { String packedDepName, Collection<String> packedDepConfigs ->
                Matcher groupMatch = packedDepName =~ /.+:(.+):.+/
                if (groupMatch.size() > 0) {
                    final List<String> match = (List<String>) groupMatch[0]
                    writePackedDependency(buildScript, match[1], packedDepName, packedDepConfigs)
                    missingPackedDepNames.remove(packedDepName)
                }
            }
            buildScript.append("}\n")

            if (!missingPackedDepNames.empty) {
                throw new RuntimeException(
                    "Looking for packed dependencies ${wantedPackedDepNames}, failed to find ${missingPackedDepNames}"
                )
            }
        }
        buildScript.append("\n")
        
        // The 'links' block.
        allLinks.writeScript(buildScript)
        
        // Generate the 'publishPackages' block:
        if (publishInfoSpecified()) {
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

    private boolean publishInfoSpecified() {
        return (publishUrl != null && publishCredentials != null) || republishHandler != null
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
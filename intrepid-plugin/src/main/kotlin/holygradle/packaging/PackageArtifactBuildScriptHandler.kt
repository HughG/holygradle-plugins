package holygradle.packaging

import holygradle.Helper
import holygradle.custom_gradle.PluginUsages
import holygradle.dependencies.DependencyHandler
import holygradle.dependencies.PackedDependencyHandler
import holygradle.io.FileHelper
import holygradle.links.LinkHandler
import holygradle.publishing.RepublishHandler
import holygradle.scm.SourceControlRepositories
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Action
import org.gradle.api.Project
import java.io.File
import java.util.*
import kotlin.reflect.KClass

class PackageArtifactBuildScriptHandler(private val project: Project) : PackageArtifactTextFileHandler {
    companion object {
        private fun <T : DependencyHandler> findDependenciesRecursive(
                project: Project,
                depNameToFind: String,
                depsExtensionName: String,
                depsExtensionItemClass: KClass<T>,
                matches: MutableCollection<T>
        ) {
            project.subprojects { p ->
                findDependenciesRecursive(p, depNameToFind, depsExtensionName, depsExtensionItemClass, matches)
            }

            val dependencies: Collection<*>? = project.extensions.findByName(depsExtensionName) as Collection<*>?
            if (dependencies != null) {
                val typedDependencies = dependencies
                        .filterIsInstance(depsExtensionItemClass.java)
                        .filter { it.targetName == depNameToFind }
                matches.addAll(typedDependencies)
            }
        }

        private fun findSourceDependencies(project: Project, sourceDepName: String): List<SourceDependencyHandler> {
            val matches = LinkedList<SourceDependencyHandler>()
            findDependenciesRecursive(project, sourceDepName, "sourceDependencies", SourceDependencyHandler::class, matches)
            return matches
        }

        private fun findPackedDependencies(project: Project, packedDepName: String): List<PackedDependencyHandler> {
            val matches = LinkedList<PackedDependencyHandler>()
            findDependenciesRecursive(project, packedDepName, "packedDependencies", PackedDependencyHandler::class, matches)
            return matches
        }

        private fun collectSourceDependencies(
                project: Project,
                throwIfAnyMissing: Boolean,
                sourceDepNames: Iterable<String>
        ): Map<String, SourceDependencyHandler> {
            val allSourceDeps = mutableMapOf<String, MutableList<SourceDependencyHandler>>().withDefault { ArrayList<SourceDependencyHandler>() }
            for (sourceDepName in sourceDepNames) {
                for (sourceDep in findSourceDependencies(project.rootProject, sourceDepName)) {
                    allSourceDeps[sourceDepName]!!.add(sourceDep)
                }
            }
            var sourceDependencyPathsClash = false
            for ((sourceDepName, handlersMatchingName) in allSourceDeps) {
                val handlersByPath = handlersMatchingName.groupBy { it.fullTargetPathRelativeToRootProject }
                if (handlersByPath.size > 1) {
                    sourceDependencyPathsClash = true
                    project.logger.error(
                            "The name '${sourceDepName}' is used by source dependencies targetting more than one path:"
                    )
                    for ((path, handlers) in handlersByPath) {
                        project.logger.error("  path '${File(project.rootProject.projectDir, path)}' is targetted by")
                        for (handler in handlers) {
                            project.logger.error("    source dependency '${handler.name}' in ${handler.project}")
                        }
                    }
                }
            }
            if (sourceDependencyPathsClash) {
                throw RuntimeException("Failed to find a unique target path for some source dependency names")
            }
            if (throwIfAnyMissing) {
                val wantedSourceDepNames = TreeSet<String>()
                wantedSourceDepNames += sourceDepNames
                val foundSourceDepNames = TreeSet<String>(allSourceDeps.keys)
                val missingSourceDepNames = wantedSourceDepNames - foundSourceDepNames
                if (!missingSourceDepNames.isEmpty()) {
                    throw RuntimeException(
                            "Looking for source dependencies ${wantedSourceDepNames}, failed to find ${missingSourceDepNames}"
                    )
                }
            }
            // We may have more than one handler for each name, but we know they all point to the same path, so any will do.
            return allSourceDeps.entries.associate { (name, handlers) -> name to handlers[0] }
        }

        private fun collectPackedDependencies(
                project: Project,
                packedDepNames: Collection<String>
        ): Map<String, PackedDependencyHandler> {
            val allPackedDeps = mutableMapOf<String, PackedDependencyHandler>()
            packedDepNames.forEach { packedDepName ->
                findPackedDependencies(project.rootProject, packedDepName).forEach {
                    allPackedDeps[packedDepName] = it
                }
            }
            return allPackedDeps
        }

        private fun writePackedDependency(
                buildScript: StringBuilder,
                name: String,
                fullCoordinate: String,
                configs: Collection<String>
        ) {
            buildScript.append("    \"")
            buildScript.append(name)
            buildScript.append("\" {\n")
            buildScript.append("        dependency \"")
            buildScript.append(fullCoordinate)
            buildScript.append("\"\n")
            buildScript.append("        configuration ")
            val quoted = configs.map { "\"${it}\"" }
            buildScript.append(quoted.joinToString(", "))
            buildScript.append("\n")
            buildScript.append("    }\n")
        }
    }

    private var atTop = true
    private val textAtTop = mutableListOf<String>()
    private val pinnedSourceDependencies = mutableSetOf<String>()
    private val packedDependencies = mutableMapOf<String, Collection<String>>()
    private val textAtBottom = mutableListOf<String>()
    private val ivyRepositories = mutableListOf<String>()
    private var republishHandler: RepublishHandler? = null
    private var myCredentialsConfig: String? = null
    private var publishUrl: String? = null
    private var publishCredentials: String? = null
    var generateSettingsFileForSubprojects = true
    var unpackToCache = false
    var createPackedDependenciesSettingsFile = false

    fun add(text: String) {
        if (atTop) {
            textAtTop.add(text)
        } else {
            textAtBottom.add(text)
        }
    }

    fun addIvyRepository(url: String) {
        addIvyRepository(url, null)
    }

    fun addIvyRepository(url: String, myCredentialsConfig: String?) {
        ivyRepositories.add(url)
        this.myCredentialsConfig = myCredentialsConfig
        atTop = false
    }

    fun addPublishPackages(url: String, myCredentialsConfig: String) {
        publishUrl = url
        publishCredentials = myCredentialsConfig
    }

    fun addPinnedSourceDependency(vararg sourceDep: String) {
        addPinnedSourceDependency(sourceDep.asIterable())
    }

    fun addPinnedSourceDependency(sourceDep: Iterable<String>) {
        pinnedSourceDependencies.addAll(sourceDep)
        atTop = false
    }

    fun addPinnedSourceDependency(vararg sourceDep: SourceDependencyHandler) {
        addPinnedSourceDependency(sourceDep.map { it.targetName })
        atTop = false
    }

    fun addPackedDependency(packedDepName: String, configurations: Iterable<String>) {
        if (configurations.firstOrNull() == null) {
            throw RuntimeException(
                "Packed dependency ${packedDepName} was added with no configurations; need at least one."
            )
        }
        if (packedDependencies.containsKey(packedDepName)) {
            throw RuntimeException("Packed dependency ${packedDepName} has already been added.")
        }
        packedDependencies[packedDepName] = configurations.toList()
        atTop = false
    }

    fun addPackedDependency(packedDepName: String, vararg configurations: String) {
        addPackedDependency(packedDepName, configurations.asIterable())
    }

    fun addPackedDependency(dep: DependencyHandler, vararg configurations: String) {
        addPackedDependency(dep.targetName, configurations.asIterable())
    }

    fun addRepublishing(action: Action<RepublishHandler>) {
        if (republishHandler == null) {
            republishHandler = RepublishHandler()
        }
        action.execute(republishHandler)
    }

    fun buildScriptRequired(): Boolean {
        return (listOf(
            textAtTop,
            textAtBottom,
            pinnedSourceDependencies,
            ivyRepositories
        )).any { !it.isEmpty() } ||
            !packedDependencies.isEmpty() ||
            publishInfoSpecified()
    }

    override val name: String = "build.gradle"

    override fun writeFile(targetFile: File) {
        FileHelper.ensureMkdirs(targetFile.parentFile, "as output folder for build script ${targetFile}")

        val buildScript = StringBuilder()

        // Text at the top of the build script
        for (it in textAtTop) {
            buildScript.append(it)
            buildScript.append("\n")
        }

        // Include plugins
        buildScript.append("buildscript {\n")
        val pluginUsagesExtension = project.extensions.findByName("pluginUsages") as PluginUsages
        for ((name, versions) in pluginUsagesExtension.mapping) {
            buildScript.append("    gplugins.use \"${name}:${versions.requested}\"")
        }
        buildScript.append("}\n")
        buildScript.append("gplugins.apply()\n")
        buildScript.append("\n")

        // Add repositories
        if (ivyRepositories.isNotEmpty()) {
            buildScript.append("repositories {\n")
            ivyRepositories.forEach { repo ->
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
        val allLinks = LinkHandler()
        val thisLinkHandler = project.extensions.findByName("links") as LinkHandler
        allLinks.addFrom(project.name, thisLinkHandler)

        // sourceDependencies block
        val pinnedSourceDeps = collectSourceDependencies(project, true, pinnedSourceDependencies).values
        if (pinnedSourceDeps.isNotEmpty()) {
            if (!generateSettingsFileForSubprojects) {
                buildScript.append("fetchAllDependencies {\n")
                buildScript.append("    generateSettingsFileForSubprojects = false\n")
                buildScript.append("}\n")
                buildScript.append("\n")
            }

            buildScript.append("sourceDependencies {\n")
            for (sourceDep in pinnedSourceDeps) {
                // In this case, we create a new SourceControlRepository instead of trying to get the "sourceControl"
                // extension from the sourceDep.project, because that project itself may not have the intrepid plugin
                // applied, in which case it won't have that extension.  We need to create the SourceControlRepository
                // object, instead of just using the SourceDependencyHandler, to create the actual revision.
                val repo = SourceControlRepositories.create(project.rootProject, sourceDep.absolutePath)
                if (repo != null) {
                    buildScript.append(" ".repeat(4))
                    buildScript.append("\"")
                    buildScript.append(sourceDep.fullTargetPathRelativeToRootProject)
                    buildScript.append("\" {\n")
                    buildScript.append(" ".repeat(8))
                    buildScript.append(repo.protocol)
                    buildScript.append(" \"")
                    buildScript.append(repo.url.replace("\\", "/"))
                    buildScript.append("@")
                    buildScript.append(repo.revision)
                    buildScript.append("\"\n")
                    buildScript.append(" ".repeat(4))
                    buildScript.append("}\n")
                }
            }
            buildScript.append("}\n")
        }

        if (packedDependencies.isNotEmpty()) {
            val allConfs = mutableListOf<Map.Entry<String, String>>()
            for ((depName, depConfs) in packedDependencies) {
                for (c in depConfs) {
                    Helper.parseConfigurationMapping(c, allConfs, "Formatting error for '$depName' in 'addPackedDependency'.")
                }
            }
            val confSet = allConfs.map { it.key }.toMutableSet()

            buildScript.append("configurations {\n")
            for (it in confSet.sorted()) {
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

        if (packedDependencies.isNotEmpty()) {
            val wantedPackedDepNames = TreeSet<String>(packedDependencies.keys)
            val missingPackedDepNames = TreeSet<String>(packedDependencies.keys)

            buildScript.append("packedDependencies {\n")
            // The user may want to treat a build-time source dependency as a use-time packed dependency, so first check
            // the source dependencies.
            val sourceDeps = collectSourceDependencies(project, false, packedDependencies.keys)
            for ((sourceDepName, sourceDep) in sourceDeps) {
                writePackedDependency(
                    buildScript,
                    sourceDep.fullTargetPathRelativeToRootProject,
                    sourceDep.dependencyCoordinate,
                    packedDependencies[sourceDepName]
                            ?: throw RuntimeException("Failed to find packed dependency for source dependency ${sourceDepName}")
                )
            }
            missingPackedDepNames.removeAll(sourceDeps.keys)
            val packedDeps = collectPackedDependencies(project, packedDependencies.keys)
            for ((packedDepName, packedDep) in packedDeps) {
                writePackedDependency(
                    buildScript,
                    packedDep.fullTargetPathRelativeToRootProject,
                    packedDep.dependencyCoordinate,
                    packedDependencies[packedDepName]
                            ?: throw RuntimeException("Failed to find packed dependency ${packedDepName}")
                )
            }
            missingPackedDepNames.removeAll(packedDeps.keys)
            // Some packed dependencies will explicitly specify the full coordinate, so just
            // publish them as-is.
            packedDependencies.forEach{ (packedDepName, packedDepConfigs) ->
                val match = ".+:(.+):.+".toRegex().matchEntire(packedDepName)
                if (match != null) {
                    val (name) = match.destructured
                    writePackedDependency(buildScript, name, packedDepName, packedDepConfigs)
                    missingPackedDepNames.remove(packedDepName)
                }
            }
            buildScript.append("}\n")

            if (missingPackedDepNames.isNotEmpty()) {
                throw RuntimeException(
                    "Looking for packed dependencies ${wantedPackedDepNames}, failed to find ${missingPackedDepNames}"
                )
            }
        }
        buildScript.append("\n")

        // The 'links' block.
        allLinks.writeScript(buildScript)

        // Generate the 'publishPackages' block:
        if (publishInfoSpecified()) {
            buildScript.append("publishPackages {\n")
            if (publishUrl != null && publishCredentials != null) {
                buildScript.append("    group \"")
                buildScript.append(project.group)
                buildScript.append("\"\n")
                buildScript.append("    nextVersionNumber \"")
                buildScript.append(project.version)
                buildScript.append("\"\n")
                buildScript.append("    repositories {\n")
                buildScript.append("        ivy {\n")
                buildScript.append("            credentials {\n")
                buildScript.append("                username my.username(\"")
                buildScript.append(publishCredentials)
                buildScript.append("\")\n")
                buildScript.append("                password my.password(\"")
                buildScript.append(publishCredentials)
                buildScript.append("\")\n")
                buildScript.append("            }\n")
                buildScript.append("        }\n")
                buildScript.append("        url \"")
                buildScript.append(publishUrl)
                buildScript.append("\"\n")
                buildScript.append("    }\n")
            }
            republishHandler?.writeScript(buildScript, 4)
            buildScript.append("}\n")
            buildScript.append("\n")
        }

        // Text at the bottom of the build script
        textAtBottom.forEach {
            buildScript.append(it)
            buildScript.append("\n")
        }

        targetFile.writeText(buildScript.toString())
    }

    private fun publishInfoSpecified(): Boolean {
        return (publishUrl != null && publishCredentials != null) || republishHandler != null
    }
}
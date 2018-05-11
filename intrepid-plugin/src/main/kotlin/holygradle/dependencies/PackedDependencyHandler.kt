package holygradle.dependencies

import holygradle.Helper
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import holygradle.kotlin.dsl.getValue

class PackedDependencyHandler(
        depName: String,
        project: Project
) : DependencyHandler(depName, project) {
    companion object {
        @JvmStatic
        fun createContainer(project: Project): Collection<PackedDependencyHandler> {
            if (project == project.rootProject) {
                project.extensions.create("packedDependenciesDefault", PackedDependencyHandler::class.java, "rootDefault")
            } else {
                project.extensions.create("packedDependenciesDefault", PackedDependencyHandler::class.java, "default", project.rootProject)
            }
            val packedDependencies = project.container(PackedDependencyHandler::class.java) { name: String ->
                PackedDependencyHandler(name, project)
            }
            project.extensions.add("packedDependencies", packedDependencies)
            return packedDependencies
        }
    }

    private val projectForHandler: Project get() = project
    private var dependencyId: ModuleVersionIdentifier? = null
    var applyUpToDateChecks: Boolean? = null
    var readonly: Boolean? = null
    var unpackToCache: Boolean? = null
    var createLinkToCache: Boolean? = null
        private set
    var createSettingsFile: Boolean? = null

    constructor(
        depName: String,
        projectForHandler: Project,
        dependencyCoordinate: String ,
        configurations: Collection<Map.Entry<String, String>>
    ): this(depName, projectForHandler) {
        initialiseDependencyId(dependencyCoordinate)
        this.configurationMappings.addAll(configurations)
    }
    
    constructor(
        depName: String,
        projectForHandler: Project,
        dependencyCoordinate: ModuleVersionIdentifier
    ): this(depName, projectForHandler) {
        dependencyId = dependencyCoordinate
    }
    
    private val parentHandler: PackedDependencyHandler? get() {
        val packedDependenciesDefault: PackedDependencyHandler? by projectForHandler.extensions
        return packedDependenciesDefault
    }

    val shouldUnpackToCache: Boolean get() {
        return unpackToCache ?: (parentHandler?.shouldUnpackToCache ?: true)
    }

    fun noCreateLinkToCache() {
        createLinkToCache = false
    }

    val shouldCreateLinkToCache: Boolean get() {
        // This property is different from the others: we only use the parent (default) value if it has been
        // explicitly set.  If we were to call p.shouldCreateLinkToCache(), it would always have a fallback value
        // (of p.shouldUnpackToCache()), so we'd never get to the fallback value we want, this.shouldUnpackToCache().
        return createLinkToCache ?: (parentHandler?.createLinkToCache ?: shouldUnpackToCache)
    }

    val shouldCreateSettingsFile: Boolean get() {
        return createSettingsFile ?: (parentHandler?.shouldCreateSettingsFile ?: false)
    }
    
    val shouldMakeReadonly: Boolean get() {
        return readonly ?: (parentHandler?.shouldMakeReadonly ?: !shouldApplyUpToDateChecks)
    }

    val shouldApplyUpToDateChecks: Boolean get() {
        return applyUpToDateChecks ?: (parentHandler?.shouldApplyUpToDateChecks ?: false)
    }
    
    fun initialiseDependencyId(dependencyCoordinate: String) {
        if (dependencyId != null) {
            throw RuntimeException("Cannot set dependency more than once")
        }
        val match = dependencyCoordinate.split(":")
        if (match.size != 3) {
            throw RuntimeException("Incorrect dependency coordinate format: '$dependencyCoordinate'")
        } else {
            dependencyId = DefaultModuleVersionIdentifier(match[1], match[2], match[3])
        }
    }
        
    fun dependency(dependencyCoordinate: String) {
        initialiseDependencyId(dependencyCoordinate)
    }

    override fun configuration(config: String) {
        val newConfigs: MutableCollection<Map.Entry<String, String>> = mutableListOf()
        Helper.parseConfigurationMapping(config, newConfigs, "Formatting error for '$name' in 'packedDependencies'.")
        configurationMappings.addAll(newConfigs)
        val id = getDependencyId()
        for ((fromConf, toConf) in newConfigs) {
            projectForHandler.dependencies.add(
                fromConf,
                DefaultExternalModuleDependency(
                    id.group, id.module.name, id.version, toConf
                )
            )
        }
    }

    val groupName: String get() = getDependencyId().group
    val dependencyName: String get() = getDependencyId().module.name
    val versionString: String get() = getDependencyId().version
    val dependencyCoordinate: String get() = getDependencyId().toString()

    fun getDependencyId(): ModuleVersionIdentifier {
        return dependencyId ?:
            throw RuntimeException("'dependency' not set for packed dependency '${name}' in '${project}'")
    }

    val pathIncludesVersionNumber: Boolean get() = targetName.contains("<version>")

    fun getTargetNameWithVersionNumber(versionStr: String): String = targetName.replace("<version>", versionStr)

    fun getFullTargetPathWithVersionNumber(versionStr: String): String= name.replace("<version>", versionStr)
}

package holygradle.dependencies

import holygradle.Helper
import holygradle.kotlin.dsl.getValue
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

open class PackedDependencyHandler @JvmOverloads constructor (
        depName: String,
        project: Project,
        options: PackedDependencyOptionsHandler = PackedDependencyOptionsHandler()
) :
        DependencyHandler(depName, project),
        PackedDependencyOptions by options
{
    companion object {
        @JvmStatic
        fun createContainer(project: Project): Collection<PackedDependencyHandler> {
            if (project == project.rootProject) {
                project.extensions.create("packedDependenciesDefault", PackedDependencyOptionsHandler::class.java)
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

    private val defaultOptions: PackedDependencyOptions get() {
        val packedDependenciesDefault: PackedDependencyOptions by projectForHandler.rootProject.extensions
        return packedDependenciesDefault
    }

    override val shouldUnpackToCache: Boolean get() {
        return unpackToCache ?: (defaultOptions.shouldUnpackToCache)
    }

    val shouldCreateLinkToCache: Boolean get() {
        // This property is different from the others: we only use the parent (default) value if it has been
        // explicitly set.  If we were to call p.shouldCreateLinkToCache(), it would always have a fallback value
        // (of p.shouldUnpackToCache()), so we'd never get to the fallback value we want, this.shouldUnpackToCache().
        return createLinkToCache ?: (defaultOptions.createLinkToCache ?: shouldUnpackToCache)
    }

    override val shouldCreateSettingsFile: Boolean get() {
        return createSettingsFile ?: defaultOptions.shouldCreateSettingsFile
    }
    
    override val shouldMakeReadonly: Boolean get() {
        return readonly ?: defaultOptions.shouldMakeReadonly
    }

    override val shouldApplyUpToDateChecks: Boolean get() {
        return applyUpToDateChecks ?: defaultOptions.shouldApplyUpToDateChecks
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

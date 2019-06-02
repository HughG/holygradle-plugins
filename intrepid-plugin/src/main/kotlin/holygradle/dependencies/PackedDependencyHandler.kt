package holygradle.dependencies

import holygradle.Helper
import holygradle.kotlin.dsl.container
import holygradle.kotlin.dsl.getValue
import holygradle.kotlin.dsl.newInstance
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import javax.inject.Inject

open class PackedDependencyHandler @Inject constructor (
        depName: String,
        project: Project
) :
        DependencyHandler(depName, project),
        PackedDependencyOptions by PackedDependencyOptionsHandler()
{
    companion object {
        @JvmStatic
        fun createContainer(project: Project): Collection<PackedDependencyHandler> {
            if (project == project.rootProject) {
                project.extensions.create("packedDependenciesDefault", PackedDependencyOptionsHandler::class.java)
            }
            val packedDependencies = project.container<PackedDependencyHandler> { name: String ->
                project.objects.newInstance(name, project)
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
        val unpackToCache1 = unpackToCache
        return if (unpackToCache1 != null) {
            if (sourceOverride != null && !unpackToCache1) {
                throw RuntimeException("A source override can not be applied to a packed dependency with unpackToCache = false")
            }
            unpackToCache1
        } else {
            defaultOptions.shouldUnpackToCache
        }
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
        val parts = dependencyCoordinate.split(":")
        if (parts.size != 3) {
            throw RuntimeException("Incorrect dependency coordinate format: '$dependencyCoordinate'")
        } else {
            dependencyId = DefaultModuleVersionIdentifier(parts[0], parts[1], parts[2])
        }
    }
        
    fun dependency(dependencyCoordinate: String) {
        initialiseDependencyId(dependencyCoordinate)
    }

    /**
     * Returns the {@link SourceOverrideHandler} from the root project which corresponds to this dependency, if there is
     * one; otherwise return null.
     * @return the {@link SourceOverrideHandler} from the root project which corresponds to this dependency, if there is
     * one, otherwise null.
     */
    val sourceOverride: SourceOverrideHandler?
        get() {
            val sourceOverrides: NamedDomainObjectContainer<SourceOverrideHandler> by project.rootProject.extensions
            return sourceOverrides.find { it.dependencyCoordinate == dependencyCoordinate }
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

package holygradle.artifacts

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.util.Configurable
import org.gradle.util.ConfigureUtil

/**
 * This class represents a configuration set which is connected to a specific @{link Project}.
 */
class ProjectConfigurationSet(
        name: String,
        private val project: Project
) : DefaultConfigurationSet(name), Configurable<ProjectConfigurationSet> {
    override fun configure(closure: Closure<*>?): ProjectConfigurationSet {
        ConfigureUtil.configureSelf(closure, this)
        // Force configurations to be created
        this.getConfigurationsMap(project)
        return this
    }

    val configurationsMap: Map<Map<String, String>, Configuration>
        get() = getConfigurationsMap(project)

    val configurations: List<Configuration>
        get() = ArrayList<Configuration>(configurationsMap.values)
}

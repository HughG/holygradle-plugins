package holygradle.artifacts

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.specs.Specs

object ConfigurationHelper {
    const val OPTIONAL_CONFIGURATION_NAME = "__holygradle_optional"

    fun getFirstLevelModuleDependenciesForMaybeOptionalConfiguration(
        configuration: Configuration
    ): Set<ResolvedDependency> {
        if (configuration.name == OPTIONAL_CONFIGURATION_NAME) {
            val optionalResolvedConfiguration = configuration.resolvedConfiguration
            val optionalLenientConfiguration = optionalResolvedConfiguration.lenientConfiguration
            return optionalLenientConfiguration.getFirstLevelModuleDependencies(Specs.satisfyAll())
        } else {
            return configuration.resolvedConfiguration.firstLevelModuleDependencies
        }
    }
}

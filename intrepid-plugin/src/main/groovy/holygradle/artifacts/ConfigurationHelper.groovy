package holygradle.artifacts

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.specs.Specs

class ConfigurationHelper {
    public static final String OPTIONAL_CONFIGURATION_NAME = '__holygradle_optional'

    public static Set<ResolvedDependency> getFirstLevelModuleDependenciesForMaybeOptionalConfiguration(
        Configuration configuration
    ) {
        if (configuration.name == OPTIONAL_CONFIGURATION_NAME) {
            ResolvedConfiguration optionalResolvedConfiguration =
                configuration.resolvedConfiguration
            LenientConfiguration optionalLenientConfiguration =
                optionalResolvedConfiguration.lenientConfiguration
            return optionalLenientConfiguration.getFirstLevelModuleDependencies(
                Specs.convertClosureToSpec { true }
            )
        } else {
            return configuration.resolvedConfiguration.firstLevelModuleDependencies
        }
    }
}

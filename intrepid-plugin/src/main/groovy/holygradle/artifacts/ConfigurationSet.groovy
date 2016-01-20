package holygradle.artifacts

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * A {@code ConfigurationSet} represents a collection of configurations which are related in some way, where that way
 * is defined by a type.  Implementations of this interface and {@link ConfigurationSetType} provide ways to configure
 * those relationships.  The relationships may be explicit {@link
 * Configuration#extendsFrom(org.gradle.api.artifacts.Configuration...)} relationships, or implicit relationships
 * regarding their intended use.  For example, a configuration set might contain configurations for different native
 * platforms, or native compiler build options.
 *
 * This interface provides methods to get the names of the configurations in a given set instance
 * and {@link ConfigurationSetType} provides methods to get mappings between corresponding configurations in different
 * set instances which are based on compatible configuration set types.
 */
public interface ConfigurationSet extends Named {
    ConfigurationSetType getType()
    void setType(ConfigurationSetType type)
    void type(ConfigurationSetType type)

    Map<Map<String, String>, String> getConfigurationNames()

    Map<Map<String, String>, Configuration> getConfigurations(Project project)
}
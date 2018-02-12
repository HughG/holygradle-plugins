package holygradle.artifacts

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * A {@code ConfigurationSet} represents a collection of configurations which are related in some way, where that way
 * is defined by a "type".  Implementations of this interface and {@link ConfigurationSetType} provide ways to configure
 * those relationships.  The relationships may be explicit {@link
 * Configuration#extendsFrom(org.gradle.api.artifacts.Configuration...)} relationships, or implicit relationships
 * regarding their intended use.  For example, a configuration set might contain configurations for different native
 * platforms, or native compiler build options.
 *
 * The configurations managed through this interface are normal Gradle {@link Configuration}s in every way and can be
 * accessed and used with all the usual APIs.  This interface is just a convenience for setting up and managing
 * related configuratons.
 *
 * This interface provides methods to get the names of the configurations in a given set instance
 * and {@link ConfigurationSetType} provides methods to get mappings between corresponding configurations in different
 * set instances which are based on compatible configuration set types.
 *
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_configurationSets
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_configurationSetTypes
 */
interface ConfigurationSet : Named {
    var type: ConfigurationSetType
    fun type(type: ConfigurationSetType)

    /**
     * Returns a list of the names of the configurations which are managed by this configuration set.
     * @return A list of the names of the configurations which are managed by this configuration set.
     */
    val configurationNames: List<String>

    /**
     * Returns a list of configurations in a given project, managed by this configuration set, creating them in that
     * project if they do not already exist.
     * @return A list of in a given project, managed by this configuration set.
     */
    fun getConfigurations(project: Project): List<Configuration>
}
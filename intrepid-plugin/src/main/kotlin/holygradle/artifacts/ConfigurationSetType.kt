package holygradle.artifacts

import org.gradle.api.Named
import org.gradle.api.artifacts.Configuration

/**
 * A {@ConfigurationSetType} is a template for a set of related configurations and for the appropriate mappings from
 * those configurations to a corresponding set of configurations based on the same or some other type.
 *
 * This class provides methods to return the appropriate configuration mappings between individual configurations,
 * configuration sets, and configuration set types.  A particular subclass will only work with specific other
 * subclasses, and will throw exceptions if used with incompatible objects.
 *
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_configurationSets
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_configurationSetTypes
 */
interface ConfigurationSetType : Named {
    /**
     * Returns a mapping from configurations in the {@code source} set to configurations in the {@code target} set,
     * which will often be a one-to-one mapping but may not be.  This overload allows optional extra arguments to be
     * passed for use by the implementation in a specific subclass.
     *
     * @param attrs A key-value map for optional arguments to a specific subclass.
     * @param source The set of configurations to appear on the left/fron side of the mappings.
     * @param target The set of configurations to appear on the right/to side of the mappings.
     * @return A mapping from configurations in the {@code source} set to configurations in the {@code target} set.
     */
    fun getMappingsTo(
        attrs: Map<String, Any>,
        source: ConfigurationSet,
        target: ConfigurationSet
    ): Collection<String>

    /**
     * Returns a mapping from configurations in the {@code source} set to configurations in the {@code target} set,
     * which will often be a one-to-one mapping but may not be.
     * @param source The set of configurations to appear on the left/fron side of the mappings.
     * @param target The set of configurations to appear on the right/to side of the mappings.
     * @return A mapping from configurations in the {@code source} set to configurations in the {@code target} set.
     */
    fun getMappingsTo(
        source: ConfigurationSet,
        target: ConfigurationSet
    ): Collection<String> = getMappingsTo(mapOf(), source, target)

    /**
     * Returns a mapping from configurations in the {@code source} set to configurations in a set based on the {@code
     * target} type, which will often be a one-to-one mapping but may not be.  This overload allows optional extra
     * arguments to be passed for use by the implementation in a specific subclass.
     *
     * @param attrs A key-value map for optional arguments to a specific subclass.
     * @param source The set of configurations to appear on the left/fron side of the mappings.
     * @param target The set of configurations to appear on the right/to side of the mappings.
     * @return A mapping from configurations in the {@code source} set to configurations in the {@code target} set.
     */
    fun getMappingsTo(
        attrs: Map<String, Any>,
        source: ConfigurationSet,
        targetType: ConfigurationSetType
    ): Collection<String>

    /**
     * Returns a mapping from configurations in the {@code source} set to configurations in a set based on the {@code
     * target} type, which will often be a one-to-one mapping but may not be.
     *
     * @param source The set of configurations to appear on the left/fron side of the mappings.
     * @param target The set of configurations to appear on the right/to side of the mappings.
     * @return A mapping from configurations in the {@code source} set to configurations in the {@code target} set.
     */
    fun getMappingsTo(
        source: ConfigurationSet,
        targetType: ConfigurationSetType
    ): Collection<String> = getMappingsTo(mapOf(), source, targetType)

    /**
     * Returns a mapping from a single source configuration to the configurations in this set, which may or may not
     * include all configurations in this set.  This overload allows optional extra arguments to be passed for use by
     * the implementation in a specific subclass.
     *
     * @param attrs A key-value map for optional arguments to a specific subclass.
     * @param source The set of configurations to appear on the left/fron side of the mappings.
     * @param target The set of configurations to appear on the right/to side of the mappings.
     * @return A mapping from configurations in the {@code source} set to configurations in the {@code target} set.
     */
    fun getMappingsFrom(
        attrs: Map<String, Any>,
        source: Configuration
    ): Collection<String>

    /**
     * Returns a mapping from a single source configuration to the configurations in this set, which may or may not
     * include all configurations in this set.
     *
     * @param source The set of configurations to appear on the left/fron side of the mappings.
     * @param target The set of configurations to appear on the right/to side of the mappings.
     * @return A mapping from configurations in the {@code source} set to configurations in the {@code target} set.
     */
    fun getMappingsFrom(
        source: Configuration
    ): Collection<String> = getMappingsFrom(mapOf(), source)

    /**
     * Returns a mapping from a single source configuration to the configurations in a set based on the {@code target}
     * type, which may or may not include all configurations in the set.  This overload allows optional extra arguments
     * to be passed for use by the implementation in a specific subclass.
     *
     * @param attrs A key-value map for optional arguments to a specific subclass.
     * @param source The set of configurations to appear on the left/fron side of the mappings.
     * @param target The set of configurations to appear on the right/to side of the mappings.
     * @return A mapping from configurations in the {@code source} set to configurations in the {@code target} set.
     */
    fun getMappingsFrom(
        attrs: Map<String, Any>,
        source: Configuration,
        target: ConfigurationSet
    ): Collection<String>

    /**
     * Returns a mapping from a single source configuration to the configurations in a set based on the {@code target}
     * type, which may or may not include all configurations in the set.
     *
     * @param source The set of configurations to appear on the left/fron side of the mappings.
     * @param target The set of configurations to appear on the right/to side of the mappings.
     * @return A mapping from configurations in the {@code source} set to configurations in the {@code target} set.
     */
    fun getMappingsFrom(
        source: Configuration,
        target: ConfigurationSet
    ): Collection<String> = getMappingsFrom(mapOf(), source, target)
}
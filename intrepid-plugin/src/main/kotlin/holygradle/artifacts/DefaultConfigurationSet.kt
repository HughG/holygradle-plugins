package holygradle.artifacts

import com.google.common.collect.Sets
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import kotlin.reflect.jvm.jvmName

/**
 * This configuration set class forms its related configurations based on an instance of
 * {@link DefaultConfigurationSetType} or one of its subclasses.  It makes names for the related configurations
 * defined by its type by joining the axis value strings with underscores and adding an optional
 * {@link DefaultConfigurationSet#prefix} string.  The prefix allows a single project to have more than one
 * configuration set of the same type.
 */
open class DefaultConfigurationSet(private val name: String) : ConfigurationSet {
    companion object {
        @JvmStatic
        val INCLUDE_ALL_BINDINGS: List<LinkedHashMap<String, String>> = listOf()
        private val UNINTIALISED_TYPE = object: ConfigurationSetType {
            override fun getMappingsTo(attrs: Map<String, Any>, source: ConfigurationSet, target: ConfigurationSet): Collection<String> = TODO()
            override fun getMappingsTo(attrs: Map<String, Any>, source: ConfigurationSet, targetType: ConfigurationSetType): Collection<String> = TODO()
            override fun getMappingsFrom(attrs: Map<String, Any>, source: Configuration): Collection<String> = TODO()
            override fun getMappingsFrom(attrs: Map<String, Any>, source: Configuration, target: ConfigurationSet): Collection<String> = TODO()
            override fun getName(): String = TODO()
        }

        private fun appendAxesForTemplate(builder: StringBuilder, axes: Map<String, List<String>>) {
            if (axes.isEmpty()) {
                return
            }

            builder.append("\${")
            builder.append(axes.keys.joinToString("}_\${"))
            builder.append("}")
        }
    }

    // TODO 2017-07-25 HughG: Maybe use a non-Groovy class to handle this, if Kotlin has one?
    private val engine = SimpleTemplateEngine()
    private val initSync = Object()
    // This class uses LinkedHashMap so that the iteration order is predictable and the same as the init order.
    private lateinit var _configurationNamesMap: LinkedHashMap<Map<String, String>, String>

    var prefix: String = ""
        set(value) {
            synchronized (initSync) {
                if (this::_configurationNamesMap.isInitialized) {
                    throw RuntimeException(
                        "Cannot change prefix for configuration set ${name} after configuration names have been generated"
                    )
                }

                field = value
            }
        }

    private fun addConfigurationNames(
        axes: LinkedHashMap<String, List<String>>,
        axisValueInclusionFilters: List<Map<String,String>>,
        template: Template
    ) {
        val allValueCombinations = getSortedValueCombinations(axes)
        val axesKeys = axes.keys.toList()
        val axesSize = axes.size
        for (values in allValueCombinations) {
            val binding = mutableMapOf<String, String>()
            for (i in 0 until axesSize) {
                binding[axesKeys[i]] = values[i]
            }

            if (!shouldIncludeBinding(axisValueInclusionFilters, binding)) {
                continue
            }

            // We clone the binding before using it as a key in _configurationNamesMap, because Template#make adds an "out"
            // key which holds a PrintWriter, and we don't want that stored in our key.
            val bindingForValues = mutableMapOf<String, String>().apply { putAll(binding) }
            val nameForValues = template.make(binding).toString()
            _configurationNamesMap[bindingForValues] = nameForValues
        }
    }

    private fun getSortedValueCombinations(
        axes: LinkedHashMap<String, List<String>>
    ): List<List<String>> {
        val axisCount = axes.size
        // This uses collect instead of "*.toSet" because toSet() creates a HashSet which doesn't guarantee order over
        // time (or between versions of Java). It is desirable for the configuration set order to remain the same (and
        // tests will fail if they don't). This produces reasonable grouping of configurations while preserving order
        // across Java versions.
        val axesSets = axes.values.map { axis ->
            LinkedHashSet<String>(axis.size).apply { addAll(axis) }
        }
        // This will be in the same order as axesSets because axes is a LinkedHashMap.
        val axisNames = axes.keys.toList()

        val allValueCombinations = Sets.cartesianProduct(axesSets).toList()

        // Sort allValueCombinations into a stable order: for each combination, map each value to its index in the list
        // of values for its axis, then do a vector ordering.  For the vector ordering part, we choose to reverse the
        // significance of the values from what you might expect, treating them as more significant, the further down
        // the list you go.  This is just for backward compatibility.

        // First we map all the combinations (lists of values) to a "sort key" (list of the index in the corresponding
        // axis).  We do this as an advance step to avoid doing it repeatedly when sorting.
        val sortKeys: Map<List<String>, IntArray> = allValueCombinations.associate { values: List<String> ->
            val sortKey = IntArray(axisCount)
            values.forEachIndexed { axisIndex, value ->
                val axisName = axisNames[axisIndex]
                val reverseIndex = axisCount - 1 - axisIndex
                sortKey[reverseIndex] = axes[axisName]!!.indexOf(value)
            }
            (values to sortKey)
        }

        // Now we actually do the sort.  Lists (sortKeys) are not directly comparable, so we compare the indices
        // pairwise and use a combination of "?:", "<=>" and "inject" to use the first non-zero result we get from the
        // pairwise comparisons.
        return allValueCombinations.sortedWith(Comparator { values1: List<String>, values2: List<String> ->
            val sortKey1 = sortKeys[values1]!!
            val sortKey2 = sortKeys[values2]!!
            val pairedKeys = sortKey1.zip(sortKey2)
            pairedKeys.fold(0) { acc, value ->
                if (acc == 0) { value.first.compareTo(value.second) } else { acc }
            }
        })
    }

    // Check whether the binding matches at least one of the inclusion filters.
    protected fun shouldIncludeBinding(
        axisValueInclusionFilters: List<Map<String, String>>,
        binding: Map<String, String>
    ): Boolean {
        return axisValueInclusionFilters.isEmpty() ||
            axisValueInclusionFilters.any {
                it.all { (k, v) -> binding[k] == v }
            }
    }

    override fun getName(): String = name

    fun prefix(prefix: String) {
        this.prefix = prefix
    }

    override fun type(type: ConfigurationSetType) {
        this.type = type
    }

    override var type: ConfigurationSetType = UNINTIALISED_TYPE
        get() {
            when {
                field === UNINTIALISED_TYPE -> throw RuntimeException("Must set type for configuration set ${name}")
                else -> return field
            }
        }
        set(type) {
            synchronized(initSync) {
                if (this::_configurationNamesMap.isInitialized) {
                    throw RuntimeException(
                            "Cannot change type for configuration set ${name} after configuration names have been generated"
                    )
                }

                val defaultType = type as? DefaultConfigurationSetType
                        ?: throw UnsupportedOperationException(
                                "${this::class.java} only supports configuration set types which are ${DefaultConfigurationSetType::class.java}"
                            )
                if (defaultType.requiredAxes.isEmpty()) {
                    throw IllegalArgumentException(
                            "Type ${defaultType.name} must provide at least one required axis for configuration set ${name}"
                    )
                }

                field = defaultType
            }
        }

    val typeAsDefault: DefaultConfigurationSetType
        get() {
            return type as? DefaultConfigurationSetType
                    ?: throw UnsupportedOperationException(
                            "${this::class.jvmName} only supports configuration set types which are ${DefaultConfigurationSetType::class.jvmName}"
                    )
        }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    fun getAxes(): LinkedHashMap<String, List<String>> {
        // Can't just use "+" here because it doesn't return LinkedHashMap.
        val defaultType = typeAsDefault
        return LinkedHashMap<String, List<String>>(defaultType.requiredAxes).apply { putAll(defaultType.optionalAxes) }
    }

    /**
     * Returns a map from axis-value bindings (as defined by this set's {@link DefaultConfigurationSetType}) to the
     * actual configurations names in this set (which may add a prefix string).  The elements of this map will be the
     * same as, and in the same order as, the return value of {@link #getConfigurationNames()}.
     *
     * @return A map from axis-value bindings to the actual configuration names in this set
     */
    val configurationNamesMap: Map<Map<String, String>, String>
        get() {
            synchronized(initSync) {
                if (!this::_configurationNamesMap.isInitialized) {
                    // Calculate the number of configurations we'll end up with.  We'll have the Cartesian product of all the
                    // required and optional axis values, plus the Cartesian product of just the required parts with "_common".
                    fun <K, V> Map<K, Collection<V>>.getCombinationCount() =
                            values.fold(1) { acc, value -> acc * value.size }

                    val defaultType = typeAsDefault
                    val requiredAxesCombinationCount = defaultType.requiredAxes.getCombinationCount()
                    val optionalAxesCombinationCount = defaultType.optionalAxes.getCombinationCount()
                    _configurationNamesMap = LinkedHashMap(
                            requiredAxesCombinationCount * (optionalAxesCombinationCount + 1)
                    )
                }
                if (_configurationNamesMap.isEmpty()) {
                    val builder = StringBuilder()
                    if (prefix != "") {
                        builder.append(prefix)
                        builder.append("_")
                    }
                    val defaultType = typeAsDefault
                    appendAxesForTemplate(builder, defaultType.requiredAxes)
                    val requiredPlusCommonTemplate = engine.createTemplate(builder.toString() + "_common")
                    addConfigurationNames(defaultType.requiredAxes, defaultType.commonConfigurationsSpec, requiredPlusCommonTemplate)

                    if (!defaultType.optionalAxes.isEmpty()) {
                        builder.append('_')
                        appendAxesForTemplate(builder, defaultType.optionalAxes)
                        val requiredPlusOptionalTemplate = engine.createTemplate(builder.toString())
                        val requiredPlusOptionalAxes =
                                (LinkedHashMap<String, List<String>>().apply { putAll(defaultType.requiredAxes) })
                        requiredPlusOptionalAxes.putAll(defaultType.optionalAxes)
                        addConfigurationNames(requiredPlusOptionalAxes, INCLUDE_ALL_BINDINGS, requiredPlusOptionalTemplate)
                    }
                }
            }

            return _configurationNamesMap
        }

    override val configurationNames: List<String>
        // Construct a new list so that the result can be used freely, e.g., with Groovy's spread operator (which does
        // not work with the list type returned by LinkedHashMap#values()).
        get() = ArrayList<String>(configurationNamesMap.values)

    private fun getDescriptionForBinding(binding: Map<String, String>): String {
        val typeDescription = typeAsDefault.getDescriptionForBinding(binding)
        val addPrefixDescription = if (prefix == "") "" else ".makeSet { prefix '${prefix}' }"
        val setDescription = "configurationSets.${type.name ?: throw RuntimeException("type not set for ${this}")}"
        val mappingDescription = "configurationSet ..., ${setDescription}${addPrefixDescription}"
        return typeDescription +
            " You can use this with the Holy Gradle by adding '${mappingDescription}' " +
            "to the packedDependencies entry for this module in your project, " +
            "where '...' is a configuration or configurationSet in your project."
    }

    /**
     * Returns a map from axis-value bindings (as defined by this set's {@link DefaultConfigurationSetType}) to the
     * actual configurations in this set (which may add a prefix string to their names).  The elements of this list will
     * be the same as, and in the same order as, the return value of {@link #getConfigurations(Project)}.
     *
     * @return A map from axis-value bindings to the actual configurations in this set
     */
    fun getConfigurationsMap(project: Project): Map<Map<String, String>, Configuration> {
        val configurations = project.configurations
        // Explicitly call #getConfigurationNamesMap here to make sure the names are lazily filled in.
        val nameMap = configurationNamesMap
        // Create all the configurations for the axis bindings.
        nameMap.forEach { (binding, name) ->
            val conf = configurations.findByName(name) ?: configurations.create(name)
            conf.description = getDescriptionForBinding(binding)
        }
        // Also "secretly" create the non-visible configurations, if they don't already exist -- but don't return them.
        val defaultType = typeAsDefault
        defaultType.nonVisibleConfigurations.forEach { name ->
            val conf = configurations.findByName(name) ?: configurations.create(name)
            conf.isVisible = false
            if (conf.description == null) {
                conf.description = defaultType.getDescriptionForNonVisibleConfiguration(name)
            }
        }

        // Build a map from bindings to configurations.
        val result: Map<Map<String, String>, Configuration> = nameMap.entries.associate { (k, v) ->
            k to configurations.getByName(v)
        }

        // Make the configurations with optional parts extend from the corresponding ones without those parts.
        //
        // TODO 2015-10-30 HUGR: Do this more efficiently, possibly by making a binding more than just a map.
        val optionalAxisNames = defaultType.optionalAxes.keys
        result.forEach { binding, configuration ->
            if (binding.keys.any { optionalAxisNames.contains(it) }) {
                val bindingWithoutOptionalParts = binding.filter { !optionalAxisNames.contains(it.key) }
                if (!shouldIncludeBinding(defaultType.commonConfigurationsSpec, bindingWithoutOptionalParts)) {
                    return@forEach
                }

                val commonConfigurationName = nameMap[bindingWithoutOptionalParts]
                        ?: throw RuntimeException(
                                "Failed to find configuration name in set ${name} (${nameMap.values}) (for ${project}) " +
                                "after reducing ${binding} to ${bindingWithoutOptionalParts}"
                        )
                val commonConfiguration = configurations.findByName(commonConfigurationName)
                        ?: throw RuntimeException(
                            "Failed to find configuration ${commonConfigurationName} " +
                            "from set ${name} (${nameMap.values}) in ${project} " +
                            "after reducing ${binding} to ${bindingWithoutOptionalParts}"
                        )
                configuration.extendsFrom(commonConfiguration)
            }
        }

        return result
    }

    override fun getConfigurations(project: Project): List<Configuration> {
        // Construct a new list so that the result can be used freely, e.g., with Groovy's spread operator (which does
        // not work with the list type returned by LinkedHashMap#values()).
        return ArrayList<Configuration>(getConfigurationsMap(project).values)
    }

    override fun toString(): String {
        return "DefaultConfigurationSet{" +
            "name='" + name + '\'' +
            ", type=" + type.name +
            ", configurationNamesMap=" + configurationNamesMap +
            '}'
    }
}

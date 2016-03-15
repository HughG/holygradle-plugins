package holygradle.artifacts

import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.gradle.api.Project
import com.google.common.collect.Sets
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

/**
 * This configuration set class forms its related configurations based on an instance of
 * {@link DefaultConfigurationSetType} or one of its subclasses.  It makes names for the related configurations
 * defined by its type by joining the axis value strings with underscores and adding an optional
 * {@link DefaultConfigurationSet#prefix} string.  The prefix allows a single project to have more than one
 * configuration set of the same type.
 */
class DefaultConfigurationSet implements ConfigurationSet {
    private static final List<Map<String, String>> INCLUDE_ALL_BINDINGS =
        new ArrayList<LinkedHashMap<String, String>>().asImmutable()

    private final String name
    private final SimpleTemplateEngine engine = new SimpleTemplateEngine()
    private DefaultConfigurationSetType type
    private final Object initSync = new Object()
    // This class uses LinkedHashMap so that the iteration order is predictable and the same as the init order.
    private LinkedHashMap<Map<String, String>, String> configurationNamesMap = new LinkedHashMap<>()

    String prefix

    public DefaultConfigurationSet(
        String name
    ) {
        this.name = name
    }

    private static void appendAxesForTemplate(StringBuilder builder, LinkedHashMap<String, List<String>> axes) {
        if (axes.isEmpty()) {
            return
        }

        builder.append('\${')
        builder.append(axes.keySet().join('}_\${'))
        builder.append('}')
    }

    private void addConfigurationNames(
        LinkedHashMap<String, List<String>> axes,
        List<Map<String,String>> axisValueInclusionFilters,
        Template template
    ) {
        int axesSize = axes.size()
        // IntelliJ mistakenly thinks this is a Set<List<List<String>>>
        //noinspection GroovyAssignabilityCheck
        // This is sorted because toSet() creates a HashSet which doesn't guarantee order over time (or between versions
        // of Java). It is desirable for the configuration set order to remain the same (and tests will fail if they
        // don't). This produces reasonable grouping of configurations while preserving order across Java versions.
        def axesSets = axes.values().collect {
            def set = new LinkedHashSet<List<String>>(it.size())
            set.addAll(it)
            return set
        }

        Set<List<String>> allValueCombinations = Sets.cartesianProduct(axesSets)
        List<String> axesKeys = axes.keySet().toList()
        for (List<String> values in allValueCombinations) {
            Map<String, String> binding = [:]
            for (int i = 0; i < axesSize; i++) {
                binding[axesKeys[i]] = values[i]
            }

            if (!shouldIncludeBinding(axisValueInclusionFilters, binding)) {
                continue
            }

            // We clone the binding before using it as a key in configurationNamesMap, because Template#make adds an "out"
            // key which holds a PrintWriter, and we don't want that stored in our key.
            Map<String, String> bindingForValues = binding.clone() as Map<String, String>
            String nameForValues = template.make(binding).toString()
            configurationNamesMap[bindingForValues] = nameForValues
        }
    }

    // Check whether the binding matches at least one of the inclusion filters.
    protected boolean shouldIncludeBinding(
        List<Map<String, String>> axisValueInclusionFilters,
        Map<String, String> binding
    ) {
        return axisValueInclusionFilters.isEmpty() ||
            axisValueInclusionFilters.any {
                it.every { k, v -> binding[k] == v }
            }
    }

    @Override
    String getName() {
        name
    }

    public void prefix(String prefix) {
        synchronized (initSync) {
            if (!configurationNamesMap.isEmpty()) {
                throw new RuntimeException(
                    "Cannot change prefix for configuration set ${name} after configuration names have been generated"
                )
            }

            this.prefix = prefix
        }
    }

    @Override
    void type(ConfigurationSetType type) {
        synchronized (initSync) {
            if (!configurationNamesMap.isEmpty()) {
                throw new RuntimeException(
                    "Cannot change type for configuration set ${name} after configuration names have been generated"
                )
            }

            setType(type)
        }
    }

    @Override
    void setType(ConfigurationSetType type) {
        DefaultConfigurationSetType defaultType = type as DefaultConfigurationSetType
        if (defaultType == null) {
            throw new UnsupportedOperationException(
                "${this.class} only supports configuration set types which are ${DefaultConfigurationSetType.class}"
            )
        }
        if (defaultType.requiredAxes.isEmpty()) {
            throw new IllegalArgumentException(
                "Type ${defaultType.name} must provide at least one required axis for configuration set ${name}"
            )
        }

        this.type = defaultType

        // Calculate the number of configurations we'll end up with.  We'll have the Cartesian product of all the
        // required and optional axis values, plus the Cartesian product of just the required parts with "_common".
        final int requiredAxesCombinationCount =
            defaultType.requiredAxes.values()*.size().inject(1) { acc, val -> acc * val }
        final int optionalAxesCombinationCount =
            defaultType.optionalAxes.values()*.size().inject(1) { acc, val -> acc * val }
        configurationNamesMap = new LinkedHashMap<Map<String, String>, String>(
            requiredAxesCombinationCount * (optionalAxesCombinationCount + 1)
        )
    }

    @Override
    ConfigurationSetType getType() {
        type
    }

    public final DefaultConfigurationSetType getTypeAsDefault() {
        type
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @SuppressWarnings("GroovyUnusedDeclaration")
    public final LinkedHashMap<String, List<String>> getAxes() {
        // Can't just use "+" here because it doesn't return LinkedHashMap.
        def result = new LinkedHashMap<String, List<String>>(type.requiredAxes)
        result.putAll(type.optionalAxes)
        return result
    }

    /**
     * Returns a map from axis-value bindings (as defined by this set's {@link DefaultConfigurationSetType}) to the
     * actual configurations names in this set (which may add a prefix string).  The elements of this map will be the
     * same as, and in the same order as, the return value of {@link #getConfigurationNames()}.
     *
     * @return A map from axis-value bindings to the actual configuration names in this set
     */
    public Map<Map<String, String>, String> getConfigurationNamesMap() {
        synchronized (initSync) {
            if (configurationNamesMap.isEmpty()) {
                if (type == null) {
                    throw new RuntimeException("Must set type for configuration set ${name}")
                }

                StringBuilder builder = new StringBuilder()
                if (prefix) {
                    builder.append(prefix)
                    builder.append("_")
                }
                appendAxesForTemplate(builder, type.requiredAxes)
                Template requiredPlusCommonTemplate = engine.createTemplate(builder.toString() + "_common")
                addConfigurationNames(type.requiredAxes, type.commonConfigurationsSpec, requiredPlusCommonTemplate)

                if (!type.optionalAxes.isEmpty()) {
                    builder.append('_')
                    appendAxesForTemplate(builder, type.optionalAxes)
                    Template requiredPlusOptionalTemplate = engine.createTemplate(builder.toString())
                    LinkedHashMap<String, List<String>> requiredPlusOptionalAxes =
                        (type.requiredAxes.clone() as LinkedHashMap<String, List<String>>)
                    requiredPlusOptionalAxes.putAll(type.optionalAxes)
                    addConfigurationNames(requiredPlusOptionalAxes, INCLUDE_ALL_BINDINGS, requiredPlusOptionalTemplate)
                }
            }
        }

        configurationNamesMap
    }

    @Override
    public List<String> getConfigurationNames() {
        // Construct a new list so that the result can be used freely, e.g., with Groovy's spread operator (which does
        // not work with the list type returned by LinkedHashMap#values()).
        return new ArrayList<String>(getConfigurationNamesMap().values())
    }

    public String getDescriptionForBinding(Map<String, String> binding) {
        String typeDescription = type.getDescriptionForBinding(binding)
        String addPrefixDescription = (prefix == null) ? "" : ".makeSet { prefix '${prefix}' }"
        String setDescription = "configurationSets.${type.name}"
        String mappingDescription = "configurationSet ..., ${setDescription}${addPrefixDescription}"
        return typeDescription +
            " You can use this with the Holy Gradle by adding '${mappingDescription}' " +
            "to the packedDependencies entry for this module in your project, " +
            "where '...' is a configuration or configurationSet in your project."
    }

    /**
     * Returns a map from axis-value bindings (as defined by this set's {@link DefaultConfigurationSetType}) to the
     * actual configurations in this set (which may add a prefix string to their names).  The elements of this list will
     * be the same as, and in the same order as, the return value of {@link #getConfigurations()}.
     *
     * @return A map from axis-value bindings to the actual configurations in this set
     */
    public Map<Map<String, String>, Configuration> getConfigurationsMap(Project project) {
        ConfigurationContainer configurations = project.configurations
        // Explicitly call #getConfigurationNamesMap here to make sure the names are lazily filled in.
        def nameMap = getConfigurationNamesMap()
        // Create all the configurations for the axis bindings.
        nameMap.each { binding, name ->
            Configuration conf = configurations.findByName(name) ?: configurations.add(name)
            conf.description = getDescriptionForBinding(binding)
        }
        // Also "secretly" create the non-visible configurations, if they don't already exist -- but don't return them.
        type.nonVisibleConfigurations.each { name ->
            Configuration conf = configurations.findByName(name) ?: configurations.add(name)
            conf.visible = false
            if (conf.description == null) {
                conf.description = type.getDescriptionForNonVisibleConfiguration(name)
            }
        }

        // Build a map from bindings to configurations.
        Map<Map<String, String>, Configuration> result = nameMap.collectEntries([:]) { k, v ->
            [k,  configurations.findByName(v)]
        }

        // Make the configurations with optional parts extend from the corresponding ones without those parts.
        //
        // TODO 2015-10-30 HUGR: Do this more efficiently, possibly by making a binding more than just a map.
        Set<String> optionalAxisNames = type.optionalAxes.keySet()
        result.each { Map<String, String> binding, Configuration configuration ->
            if (binding.keySet().any { optionalAxisNames.contains(it) }) {
                def bindingWithoutOptionalParts = binding.findAll { !optionalAxisNames.contains(it.key) }
                if (!shouldIncludeBinding(type.commonConfigurationsSpec, bindingWithoutOptionalParts)) {
                    return
                }

                def commonConfigurationName = nameMap[bindingWithoutOptionalParts]
                if (commonConfigurationName == null) {
                    throw new RuntimeException(
                        "Failed to find configuration name in set ${name} (${nameMap.values()}) (for ${project}) " +
                        "after reducing ${binding} to ${bindingWithoutOptionalParts}"
                    )
                }
                def commonConfiguration = configurations.findByName(commonConfigurationName)
                if (commonConfiguration == null) {
                    throw new RuntimeException(
                        "Failed to find configuration ${commonConfigurationName} " +
                        "from set ${name} (${nameMap.values()}) in ${project} " +
                        "after reducing ${binding} to ${bindingWithoutOptionalParts}"
                    )
                }
                configuration.extendsFrom(commonConfiguration)
            }
        }

        return result
    }

    @Override
    public List<Configuration> getConfigurations(Project project) {
        // Construct a new list so that the result can be used freely, e.g., with Groovy's spread operator (which does
        // not work with the list type returned by LinkedHashMap#values()).
        return new ArrayList<Configuration>(getConfigurationsMap(project).values())
    }

    @Override
    public String toString() {
        return "DefaultConfigurationSet{" +
            "name='" + name + '\'' +
            ", type=" + type.name +
            ", configurationNamesMap=" + getConfigurationNamesMap() +
            '}';
    }
}

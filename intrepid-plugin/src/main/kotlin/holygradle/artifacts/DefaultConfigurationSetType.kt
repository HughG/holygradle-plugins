package holygradle.artifacts

import holygradle.lang.NamedParameters
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.jetbrains.kotlin.incremental.dumpMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.component1

/**
 * This configuration set type class forms its pattern of related configurations from several axes.  Each axis has a
 * name and a fixed set of values, and the set of configuration names comes from taking each possible combination of
 * values from those axes, in the order in which the axes were defined, and combining them into a string, separating the
 * values with an underscore.  A specific combination of values for each axis is referred to as a <em>binding</em>.
 *
 * The axes are divided into required and optional lists, and further names are generated by combining values from the
 * required axes with a single "{@code _common}" suffix.  The point of this is to support languages like C++ where some
 * dependencies and artifacts are platform-specific (using the optional axes) and some are platform-independent (when
 * the optional axes are replaced with "{@code _common}").  For example, in a typical use with Visual C++ there would be
 * a single required axis "stage", with values "{@code import}", "{@code runtime}", and "{@code debugging}", plus
 * optional axes "Platform" and "Configuration".  The link libraries for a C++ library would be related to
 * configurations such as {@code import_x64_Release} and {@code import_Win32_Release}, and the header files would be
 * related to {@code import_common}.  The configurations with the more specific axis values are intended to extend from
 * the corresponding common configurations -- because, for example, using C++ link libraries generally also require the
 * headers.
 *
 * This class also allows for a number of configurations which do not fit the above pattern and are marked as not
 * visible.  These are to support details of configuration mapping.  The subclasses of this class keep the same logic
 * for constructing names and add specific configuration mapping rules for the {@code import} axis.
 *
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_configurationSetTypes
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_pre_defined_configuration_set_types
 */
open class DefaultConfigurationSetType(
        private val _name: String,
        var requiredAxes: LinkedHashMap<String, List<String>>,
        var optionalAxes: LinkedHashMap<String, List<String>>,
        val nonVisibleConfigurations: LinkedHashSet<String>
) : ConfigurationSetType {
    companion object {
        const val STAGE_AXIS_NAME = "stage"
        const val IMPORT_STAGE = "import"
        const val RUNTIME_STAGE = "runtime"
        const val DEBUGGING_STAGE = "debugging"
        const val PRIVATE_BUILD_CONFIGURATION_NAME = "build"

        // TODO 2017-07-26 HughG: Add overload which takes maybeAddMapping as a Groovy Closure and checks that it fits,
        // so that this class can be subclassed in Groovy.
        @JvmStatic
        protected fun getDefaultMappingsTo(
                source: DefaultConfigurationSet,
                target: DefaultConfigurationSet,
                maybeAddMapping: (MutableList<String>, Map<String, String>, String, String) -> Unit
        ): Collection<String> {
            val sourceConfigurations = source.configurationNamesMap
            val targetConfigurations = target.configurationNamesMap

            val sourceType = source.typeAsDefault
            val sourceOptionalAxesNames = sourceType.optionalAxes.keys
            val mappings = ArrayList<String>(sourceConfigurations.size)
            for ((binding, sourceConfigurationName) in sourceConfigurations) {
                if (!binding.keys.any { sourceOptionalAxesNames.contains(it) }) {
                    // Don't map this; only map configurations which have all parts specified.
                    continue
                }

                val targetConfigurationName = targetConfigurations[binding]
                        ?: throw RuntimeException(
                        "Failed to find target for ${binding} (configuration name ${sourceConfigurationName}) " +
                                "in ${targetConfigurations}"
                )

                maybeAddMapping(mappings, binding, sourceConfigurationName, targetConfigurationName)
            }

            return mappings
        }

        // TODO 2017-07-26 HughG: Add overload which takes maybeAddMapping as a Groovy Closure and checks that it fits,
        // so that this class can be subclassed in Groovy.
        protected fun getDefaultMappingsTo(
                source: Configuration,
                target: DefaultConfigurationSet,
                maybeAddMapping: (MutableList<String>, Map<String, String>, String, String) -> Unit
        ): Collection<String> {
            val targetConfigurations = target.configurationNamesMap
            val targetOptionalAxesNames = target.typeAsDefault.optionalAxes.keys
            val mappings = ArrayList<String>(targetConfigurations.size)
            for ((binding, targetConfigurationName) in targetConfigurations) {
                if (!binding.keys.any { targetOptionalAxesNames.contains(it) }) {
                    // Don't map this; only map configurations which have all parts specified.
                    continue
                }

                maybeAddMapping(mappings, binding, source.name, targetConfigurationName)
            }

            return mappings
        }

        @JvmStatic
        protected fun makeMapping(from: String, to: String): String = "${from}->${to}"

        @JvmStatic
        protected fun getMappingAdder(
            export: Boolean
        ): (MutableList<String>, Map<String, String>, String, String) -> Unit {
            return {
                mappings: MutableList<String>,
                binding: Map<String, String>,
                sourceConfigurationName: String,
                targetConfigurationName: String
            ->
                if (binding["stage"] == IMPORT_STAGE) {
                    // Link import to a public config iff export == true
                    val sourceName = if (export) sourceConfigurationName else PRIVATE_BUILD_CONFIGURATION_NAME
                    mappings.add(makeMapping(sourceName, targetConfigurationName))
                } else {
                    mappings.add(makeMapping(sourceConfigurationName, targetConfigurationName))
                }
            }
        }
    }

    private val nextSetId = AtomicInteger()

    /**
     * Returns a list of axis-value partial bindings for which a "{@code _common}" configuration should be created.  In
     * this case "partial" means that the binding may not have values for all axes.  Indeed, it should only have
     * bindings for some or all of the required axes.  For example, if the list contains only {@code
     * [["stage": "import"]]} then only {@code import_common} should be created, but if it contains
     * {@code [["stage": "import", ["stage": "runtime"]]]} then both {@code import_common} and {@code runtime_common}
     * are required.
     *
     * Within the {@link DefaultConfigurationSet} class, each binding provided by this type is matched against the
     * partial bindings returned from this method.  A "{@code _common}" configuration is created if any of the partial
     * bindings match.  A partial binding matches if all its axis values appear in the candidate binding.
     *
     * @return A list of axis-value mappings for which a "_common" configuration should be created.
     */
    val commonConfigurationsSpec = mutableListOf<LinkedHashMap<String, String>>()

    constructor (
            name: String
    ): this(name, LinkedHashMap<String, List<String>>(), LinkedHashMap<String, List<String>>(), LinkedHashSet<String>())

    constructor (
        name: String,
        requiredAxes: LinkedHashMap<String, List<String>>,
        optionalAxes: LinkedHashMap<String, List<String>>
    ): this(name, requiredAxes, optionalAxes, LinkedHashSet<String>())

    init {
        nonVisibleConfigurations.add(PRIVATE_BUILD_CONFIGURATION_NAME)
    }

    override fun getName(): String = _name

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @Suppress("unused")
    fun requiredAxes(requiredAxes: Map<String, List<String>>) {
        this.requiredAxes.putAll(requiredAxes)
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @Suppress("unused")
    fun optionalAxes(optionalAxes: Map<String, List<String>>) {
        this.optionalAxes.putAll(optionalAxes)
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @Suppress("unused")
    val axes: LinkedHashMap<String, List<String>>
        get() {
            return LinkedHashMap<String, List<String>>().apply {
                putAll(requiredAxes)
                putAll(optionalAxes)
            }
        }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @Suppress("unused")
    fun nonVisibleConfigurations(nonVisibleConfigurations: Set<String>) {
        this.nonVisibleConfigurations.addAll(nonVisibleConfigurations)
    }

    /**
     * Adds one or more mappings to the list returned by {@link #getCommonConfigurationsSpec()}.
     * @param commonConfigurationsSpec The mappings to add.
     */
    fun commonConfigurationsSpec(vararg commonConfigurationsSpec: LinkedHashMap<String, String>) {
        commonConfigurationsSpec(commonConfigurationsSpec.toList())
    }

    /**
     * Adds one or more mappings to the list returned by {@link #getCommonConfigurationsSpec()}.
     * @param commonConfigurationsSpec The mappings to add.
     */
    fun commonConfigurationsSpec(commonConfigurationsSpec: List<LinkedHashMap<String, String>>) {
        if (requiredAxes.isEmpty()) {
            throw RuntimeException("Must set requiredAxes before setting commonConfigurationsSpec")
        }
        // If any of the specs have axes which are not in the required list, that's an error.
        val incorrectSpecs = commonConfigurationsSpec.filter { spec ->
            !(spec.keys - requiredAxes.keys).isEmpty()
        }
        if (!incorrectSpecs.isEmpty()) {
            throw RuntimeException(
                "commonConfigurationsSpec axis keys must all be in the requiredAxes map, but some were not: " +
                incorrectSpecs
            )
        }
        this.commonConfigurationsSpec.addAll(commonConfigurationsSpec)
    }

    /**
     * Crates and returns a new instance of {@link DefaultConfigurationSet} and sets its type to this type.
     * @return A new instance of {@link DefaultConfigurationSet} whose type is set to this type.
     */
    // This is an API for use by build scripts, so ignore the "unused" warning.
    @Suppress("unused")
    fun makeSet(): DefaultConfigurationSet {
        return DefaultConfigurationSet("from ${name}, #${nextSetId.getAndIncrement()}").apply {
            type(this@DefaultConfigurationSetType)
        }
    }

    /**
     * Crates and returns a new instance of {@link DefaultConfigurationSet} and sets its type to this type, then
     * configures it with the given closure.
     *
     * @return A new instance of {@link DefaultConfigurationSet} whose type is set to this type and which is configured
     * with the given closure.
     */
    // This is an API for use by build scripts, so ignore the "unused" warning.
    @Suppress("unused")
    fun makeSet(configure: Action<DefaultConfigurationSet>): DefaultConfigurationSet {
        return makeSet().apply { configure.execute(this) }
    }

    /**
     * Returns a string describing the given binding.
     * @param binding A map from axis names to axis values.
     * @return A string describing the {@code binding}.
     */
    fun getDescriptionForBinding(binding: Map<String, String>): String {
        val stage = binding[STAGE_AXIS_NAME]
        val bindingWithoutStage = binding.filter { (k, _) -> k != STAGE_AXIS_NAME }
        fun getBindingMapDescription(binding: Map<String, String>): String =
                binding.asIterable().joinToString(",", "[", "]") {
                    "${it.key}:${it.value}"
                }
        val restOfBindingDescription = if (bindingWithoutStage.isEmpty()) {
            ", common to all values of ${optionalAxes.keys}"
        } else {
            ", with ${getBindingMapDescription(bindingWithoutStage)}"
        }
        return "Files for the ${stage} stage of development${restOfBindingDescription}."
    }

    /**
     * Returns a description for one of the non-visible configurations supported by this type.
     * @param name The name of a non-visible configuration supported by this type.
     * @return A description for the configuration with the given {@code name}.
     */
    fun getDescriptionForNonVisibleConfiguration(name: String): String? {
        return when (name) {
            PRIVATE_BUILD_CONFIGURATION_NAME ->
                "Private configuration for files needed to build this module, but not needed for other modules to use it."
            else -> null
        }
    }

    override fun getMappingsTo(attrs: Map<String, Any>, source: ConfigurationSet, target: ConfigurationSet): Collection<String> {
        val defaultSource = source as? DefaultConfigurationSet
        val defaultTarget = target as? DefaultConfigurationSet
        if (defaultSource == null || defaultTarget == null) {
            throwForUnknownTypes(source, target)
        }
        return getDefaultMappingsTo(attrs, mapOf(), defaultSource, defaultTarget)
    }

    override fun getMappingsTo(attrs: Map<String, Any>, source: ConfigurationSet, targetType: ConfigurationSetType): Collection<String> {
        val defaultSource = source as? DefaultConfigurationSet
        val defaultTargetType = targetType as? DefaultConfigurationSetType
        if (defaultSource == null || defaultTargetType == null) {
            throwForUnknownTypes(source, targetType)
        }
        val defaultTarget = DefaultConfigurationSet("from configuration set ${source.name}").apply { type(targetType) }
        return getDefaultMappingsTo(attrs, mapOf(), defaultSource, defaultTarget)
    }

    override fun getMappingsFrom(attrs: Map<String, Any>, source: Configuration): Collection<String> {
        val target = DefaultConfigurationSet("from configuration ${source}").apply {
            type(this@DefaultConfigurationSetType)
        }
        return getDefaultMappingsFrom(attrs, mapOf(), source, target)
    }

    override fun getMappingsFrom(attrs: Map<String, Any>, source: Configuration, target: ConfigurationSet): Collection<String> {
        val defaultTarget = target as? DefaultConfigurationSet ?: throwForUnknownTarget(target)
        return getDefaultMappingsFrom(attrs, mapOf(), source, defaultTarget)
    }

    /**
     * This method returns a collection of configuration mapping strings of the form "a->b", mapping configurations
     * from the {@code source} to configurations in the {@code target}, according to rules which are subclass-specific.
     *
     * If you override this, you should also override {@link holygradle.artifacts
     * .DefaultConfigurationSetType#getDefaultMappingsFrom(Map, Configuration, DefaultConfigurationSet)}
     *
     * @param attrs Optional extra arguments for use by subclasses.
     * @param source The source configuration set.
     * @param target The target configuration set.
     * @return
     */
    protected open fun getDefaultMappingsTo(
        attrs: Map<String, Any>,
        parameterSpecs: Map<String, Any>,
        source: DefaultConfigurationSet,
        target: DefaultConfigurationSet
    ): Collection<String> {
        val export: Boolean = NamedParameters.checkAndGet(attrs, mapOf("export" to false) + parameterSpecs)[0] as Boolean

        return getDefaultMappingsTo(source, target, getMappingAdder(export))
    }

    /**
     * This method returns a collection of configuration mapping strings of the form "a->b", mapping the {@code source}
     * configuration to some configurations in the {@code target}, according to rules which are subclass-specific.  That
     * means, if the source configuration is "foo" then every returned string will start with "foo->".
     *
     * If you override this, you should also override {@link holygradle.artifacts
     * .DefaultConfigurationSetType#getDefaultMappingsTo(Map, Map, DefaultConfigurationSet, DefaultConfigurationSet)}

     * @param attrs Optional extra arguments for use by subclasses.
     * @param source The source configuration name (not configuration set).
     * @param target The target configuration set.
     * @return
     */
    protected open fun getDefaultMappingsFrom(
        attrs: Map<String, Any>,
        parameterSpecs: Map<String, Any>,
        source: Configuration,
        target: DefaultConfigurationSet
    ): Collection<String> {
        val export: Boolean = NamedParameters.checkAndGet(attrs, mapOf("export" to false) + parameterSpecs)[0] as Boolean

        return getDefaultMappingsTo(source, target, getMappingAdder(export))
    }

    protected fun throwForUnknownTarget(target: ConfigurationSet): Nothing {
        throw RuntimeException(
            "${this::class.java} only supports configuration sets of class ${DefaultConfigurationSet::class.java} " +
                "and configuration types of class ${WindowsConfigurationSetType::class.java} " +
                "but received a target of ${target::class.java}"
        )
    }

    protected fun throwForUnknownTargetType(targetType: ConfigurationSetType): Nothing {
        throw RuntimeException(
            "${this::class.java} only supports configuration sets of class ${DefaultConfigurationSet::class.java} " +
                "and configuration types of class ${WindowsConfigurationSetType::class.java} " +
                "but received a target type of ${targetType::class.java}"
        )
    }

    protected fun throwForUnknownTypes(source: ConfigurationSet, target: ConfigurationSet): Nothing {
        throw RuntimeException(
            "${this::class.java} only supports configuration sets of class ${DefaultConfigurationSet::class.java} " +
            "and configuration types of class ${WindowsConfigurationSetType::class.java} " +
            "but received a source of ${source::class.java}, and a target of ${target::class.java}"
        )
    }

    protected fun throwForUnknownTypes(source: ConfigurationSet, targetType: ConfigurationSetType): Nothing {
        throw RuntimeException(
            "${this::class.java} only supports configuration sets of class ${DefaultConfigurationSet::class.java} " +
                "and configuration types of class ${WindowsConfigurationSetType::class.java} " +
                "but received a source of ${source::class.java}, and a target type of ${targetType::class.java}"
        )
    }

    override fun toString(): String {
        return "DefaultConfigurationSetType{" +
            "name='" + name + '\'' +
            ", requiredAxes=" + requiredAxes +
            ", optionalAxes=" + optionalAxes +
            ", nonVisibleConfigurations=" + nonVisibleConfigurations +
            '}'
    }
}

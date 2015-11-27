package holygradle.artifacts

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.util.ConfigureUtil

import java.util.concurrent.atomic.AtomicInteger

class DefaultConfigurationSetType implements ConfigurationSetType {
    public static final String IMPORT_STAGE = "import"
    public static final String RUNTIME_STAGE = "runtime"
    public static final String DEBUGGING_STAGE = "debugging"

    private final String name
    private final AtomicInteger nextSetId = new AtomicInteger()
    private LinkedHashMap<String, List<String>> requiredAxes
    private LinkedHashMap<String, List<String>> optionalAxes

    private List<Map<String, String>> commonConfigurationsSpec = new ArrayList<LinkedHashMap<String, String>>()

    public DefaultConfigurationSetType(
        String name
    ) {
        this(name, new LinkedHashMap<String, List<String>>(), new LinkedHashMap<String, List<String>>())
    }

    public DefaultConfigurationSetType(
        String name,
        LinkedHashMap<String, List<String>> requiredAxes,
        LinkedHashMap<String, List<String>> optionalAxes
    ) {
        this.name = name
        this.requiredAxes = requiredAxes
        this.optionalAxes = optionalAxes
    }

    @Override
    final String getName() {
        return name
    }

    public final LinkedHashMap<String, List<String>> getRequiredAxes() {
        return requiredAxes
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @SuppressWarnings("GroovyUnusedDeclaration")
    public final void setRequiredAxes(LinkedHashMap<String, List<String>> requiredAxes) {
        this.requiredAxes = requiredAxes
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @SuppressWarnings("GroovyUnusedDeclaration")
    public final void requiredAxes(LinkedHashMap<String, List<String>> requiredAxes) {
        this.requiredAxes.putAll(requiredAxes)
    }

    public final LinkedHashMap<String, List<String>> getOptionalAxes() {
        return optionalAxes
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @SuppressWarnings("GroovyUnusedDeclaration")
    public final void setOptionalAxes(LinkedHashMap<String, List<String>> optionalAxes) {
        this.optionalAxes = optionalAxes
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @SuppressWarnings("GroovyUnusedDeclaration")
    public final void optionalAxes(LinkedHashMap<String, List<String>> optionalAxes) {
        this.optionalAxes.putAll(optionalAxes)
    }

    public void commonConfigurationsSpec(Map<String, String> ... commonConfigurationsSpec) {
        this.commonConfigurationsSpec(commonConfigurationsSpec.toList())
    }

    public void commonConfigurationsSpec(List<Map<String, String>> commonConfigurationsSpec) {
        if (requiredAxes.isEmpty()) {
            throw new RuntimeException("Must set requiredAxes before setting commonConfigurationsSpec")
        }
        // If any of the specs have axes which are not in the required list, that's an error.
        List<Map<String,String>> incorrectSpecs = commonConfigurationsSpec.findAll { spec ->
            !(spec.keySet() - requiredAxes.keySet()).isEmpty()
        }
        if (!incorrectSpecs.isEmpty()) {
            throw new RuntimeException(
                "commonConfigurationsSpec axis keys must all be in the requiredAxes map, but some were not: " +
                incorrectSpecs
            )
        }
        this.commonConfigurationsSpec.addAll(commonConfigurationsSpec)
    }

    public List<Map<String, String>> getCommonConfigurationsSpec() {
        return commonConfigurationsSpec
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @SuppressWarnings("GroovyUnusedDeclaration")
    public DefaultConfigurationSet makeSet() {
        DefaultConfigurationSet result = new DefaultConfigurationSet("from ${name}, #${nextSetId.getAndIncrement()}")
        result.type(this)
        return result
    }

    // This is an API for use by build scripts, so ignore the "unused" warning.
    @SuppressWarnings("GroovyUnusedDeclaration")
    public DefaultConfigurationSet makeSet(Closure configure) {
        return ConfigureUtil.configure(configure, makeSet())
    }

    @Override
    public final Collection<String> getMappingsTo(
        Map attrs,
        ConfigurationSet source,
        ConfigurationSet target
    ) {
        final DefaultConfigurationSet defaultSource = source as DefaultConfigurationSet
        final DefaultConfigurationSet defaultTarget = target as DefaultConfigurationSet
        if (defaultSource == null || defaultTarget == null) {
            throwForUnknownTypes(source, target)
        }
        return getDefaultMappingsTo(attrs, defaultSource, defaultTarget)
    }

    @Override
    public final Collection<String> getMappingsTo(
        ConfigurationSet source,
        ConfigurationSet target
    ) {
        return getMappingsTo([:], source, target)
    }

    @Override
    public final Collection<String> getMappingsTo(
        Map attrs,
        ConfigurationSet source,
        ConfigurationSetType targetType
    ) {
        final DefaultConfigurationSet defaultSource = source as DefaultConfigurationSet
        final DefaultConfigurationSetType defaultTargetType = targetType as DefaultConfigurationSetType
        if (defaultSource == null || defaultTargetType == null) {
            throwForUnknownTypes(source, targetType)
        }
        DefaultConfigurationSet defaultTarget = new DefaultConfigurationSet("from configuration set ${source.name}")
        defaultTarget.type = targetType
        return getDefaultMappingsTo(attrs, defaultSource, defaultTarget)
    }

    @Override
    public final Collection<String> getMappingsTo(
        ConfigurationSet source,
        ConfigurationSetType targetType
    ) {
        return getMappingsTo([:], source, targetType)
    }

    @Override
    public final Collection<String> getMappingsFrom(Map attrs, Configuration source) {
        DefaultConfigurationSet target = new DefaultConfigurationSet("from configuration ${source}")
        target.type = this
        return getDefaultMappingsFrom(attrs, source, target)
    }

    @Override
    public final Collection<String> getMappingsFrom(Configuration source) {
        return getMappingsFrom([:], source)
    }

    @Override
    public final Collection<String> getMappingsFrom(Map attrs, Configuration source, ConfigurationSet target) {
        final DefaultConfigurationSet defaultTarget = target as DefaultConfigurationSet
        if (defaultTarget == null) {
            throwForUnknownTarget(target)
        }
        return getDefaultMappingsFrom(attrs, source, defaultTarget)
    }

    @Override
    public final Collection<String> getMappingsFrom(Configuration source, ConfigurationSet target) {
        return getMappingsFrom([:], source, target)
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
    protected Collection<String> getDefaultMappingsTo(
        Map attrs,
        DefaultConfigurationSet source,
        DefaultConfigurationSet target
    ) {
        return getDefaultMappingsTo(source, target) {
            Collection<String> mappings,
            Map<String, String> binding,
            String sourceConfigurationName,
            String targetConfigurationName
             ->
            mappings << makeMapping(sourceConfigurationName, targetConfigurationName)
        }
    }

    /**
     * This method returns a collection of configuration mapping strings of the form "a->b", mapping the {@code source}
     * configuration to some configurations in the {@code target}, according to rules which are subclass-specific.  That
     * means, if the source configuration is "foo" then every returned string will start with "foo->".
     *
     * If you override this, you should also override {@link #getDefaultMappingsTo(java.util.Map, holygradle.artifacts.DefaultConfigurationSet, holygradle.artifacts.DefaultConfigurationSet)}

     * @param attrs Optional extra arguments for use by subclasses.
     * @param source The source configuration name (not configuration set).
     * @param target The target configuration set.
     * @return
     */
    protected Collection<String> getDefaultMappingsFrom(
        Map attrs,
        Configuration source,
        DefaultConfigurationSet target
    ) {
        return getDefaultMappingsTo(source, target) {
            Collection<String> mappings,
            Map<String, String> binding,
            String sourceConfigurationName,
            String targetConfigurationName
             ->
            mappings << makeMapping(sourceConfigurationName, targetConfigurationName)
        }
    }

    protected static final Collection<String> getDefaultMappingsTo(
        DefaultConfigurationSet source,
        DefaultConfigurationSet target,
        Closure maybeAddMapping
    ) {
        Map<Map<String, String>, String> sourceConfigurations = source.configurationNames
        Map<Map<String, String>, String> targetConfigurations = target.configurationNames

        DefaultConfigurationSetType sourceType = source.typeAsDefault
        def sourceOptionalAxesNames = sourceType.optionalAxes.keySet()
        List<String> mappings = new ArrayList<String>(sourceConfigurations.size())
        sourceConfigurations.each { Map<String, String> binding, String sourceConfigurationName ->
            if (!binding.keySet().any { sourceOptionalAxesNames.contains(it) }) {
                // Don't map this; only map configurations which have all parts specified.
                return
            }

            String targetConfigurationName = targetConfigurations.get(binding)
            if (targetConfigurationName == null) {
                throw new RuntimeException(
                    "Failed to find target for ${binding} (configuration name ${sourceConfigurationName}) " +
                    "in ${targetConfigurations}"
                )
            }

            maybeAddMapping(mappings, binding, sourceConfigurationName, targetConfigurationName)
        }

        return mappings
    }

    protected static final Collection<String> getDefaultMappingsTo(
        Configuration source,
        DefaultConfigurationSet target,
        Closure maybeAddMapping
    ) {
        Map<Map<String, String>, String> targetConfigurations = target.configurationNames

        def targetOptionalAxesNames = target.typeAsDefault.optionalAxes.keySet()
        List<String> mappings = new ArrayList<String>(targetConfigurations.size())
        targetConfigurations.each { Map<String, String> binding, String targetConfigurationName ->
            if (!binding.keySet().any { targetOptionalAxesNames.contains(it) }) {
                // Don't map this; only map configurations which have all parts specified.
                return
            }

            maybeAddMapping(mappings, binding, source.name, targetConfigurationName)
        }

        return mappings
    }

    protected static final String makeMapping(String from, String to) {
        "${from}->${to}"
    }

    protected final void throwForUnknownTarget(ConfigurationSet target) {
        throw new RuntimeException(
            "${this.class} only supports configuration sets of class ${DefaultConfigurationSet.class} " +
                "and configuration types of class ${WindowsConfigurationSetType.class} " +
                "but received a target of ${target?.class}"
        )
    }

    protected final void throwForUnknownTargetType(ConfigurationSetType targetType) {
        throw new RuntimeException(
            "${this.class} only supports configuration sets of class ${DefaultConfigurationSet.class} " +
                "and configuration types of class ${WindowsConfigurationSetType.class} " +
                "but received a target type of ${targetType?.class}"
        )
    }

    protected void throwForUnknownTypes(ConfigurationSet source, ConfigurationSet target) {
        throw new RuntimeException(
            "${this.class} only supports configuration sets of class ${DefaultConfigurationSet.class} " +
            "and configuration types of class ${WindowsConfigurationSetType.class} " +
            "but received a source of ${source?.class}, and a target of ${target?.class}"
        )
    }

    protected void throwForUnknownTypes(ConfigurationSet source, ConfigurationSetType targetType) {
        throw new RuntimeException(
            "${this.class} only supports configuration sets of class ${DefaultConfigurationSet.class} " +
                "and configuration types of class ${WindowsConfigurationSetType.class} " +
                "but received a source of ${source?.class}, and a target type of ${targetType?.class}"
        )
    }

    @Override
    public String toString() {
        return "DefaultConfigurationSetType{" +
            "name='" + name + '\'' +
            ", requiredAxes=" + requiredAxes +
            ", optionalAxes=" + optionalAxes +
            '}';
    }
}

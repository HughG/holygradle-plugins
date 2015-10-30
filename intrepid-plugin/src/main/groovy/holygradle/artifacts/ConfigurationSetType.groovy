package holygradle.artifacts

import org.gradle.api.Named

// This is an API for use by build scripts, so ignore the "unused" warning.
@SuppressWarnings("GroovyUnusedDeclaration")
public interface ConfigurationSetType extends Named {
    public Collection<String> getMappingsTo(
        Map attrs,
        ConfigurationSet source,
        ConfigurationSet target
    )

    public Collection<String> getMappingsTo(
        ConfigurationSet source,
        ConfigurationSet target
    )

    public Collection<String> getMappingsTo(
        Map attrs,
        ConfigurationSet source,
        ConfigurationSetType targetType
    )

    public Collection<String> getMappingsTo(
        ConfigurationSet source,
        ConfigurationSetType targetType
    )

    public Collection<String> getMappingsFrom(
        Map attrs,
        String source
    )

    public Collection<String> getMappingsFrom(
        String source
    )
}
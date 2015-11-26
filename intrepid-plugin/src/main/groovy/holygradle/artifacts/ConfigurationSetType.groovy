package holygradle.artifacts

import org.gradle.api.Named
import org.gradle.api.artifacts.Configuration

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
        Configuration source
    )

    public Collection<String> getMappingsFrom(
        Configuration source
    )

    public Collection<String> getMappingsFrom(
        Map attrs,
        Configuration source,
        ConfigurationSet target
    )

    public Collection<String> getMappingsFrom(
        Configuration source,
        ConfigurationSet target
    )
}
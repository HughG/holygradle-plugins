package holygradle.artifacts

import groovy.transform.InheritConstructors

@InheritConstructors
class WindowsExecutableConfigurationSetType extends WindowsConfigurationSetType {
    @Override
    Collection<String> getDefaultMappingsTo(
        Map attrs,
        DefaultConfigurationSet source,
        DefaultConfigurationSet target
    ) {
        return getDefaultMappingsTo(source, target, getMappingAdder())
    }

    @Override
    public Collection<String> getMappingsFrom(
        Map attrs, String source
    ) {
        return getDefaultMappingsTo(source, this, getMappingAdder())
    }

    private static Closure getMappingAdder() {
        return {
            Collection<String> mappings,
            Map<String, String> binding,
            String sourceConfigurationName,
            String targetConfigurationName
             ->
            if (binding['stage'] == IMPORT_STAGE) {
                // Don't link import, because the source, being an executable or plugin DLL, will not be imported by
                // anything, so its import configurations should be empty.
            } else {
                mappings << makeMapping(sourceConfigurationName, targetConfigurationName)
            }
        }
    }
}

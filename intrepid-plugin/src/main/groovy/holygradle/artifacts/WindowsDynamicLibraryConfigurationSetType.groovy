package holygradle.artifacts

import groovy.transform.InheritConstructors
import holygradle.lang.NamedParameters

@InheritConstructors
class WindowsDynamicLibraryConfigurationSetType extends WindowsConfigurationSetType {
//    public WindowsDynamicLibraryConfigurationSetType(
//        String name,
//        List<String> platforms,
//        List<String> configurations
//    ) {
//        super(name, platforms, configurations)
//    }

    @Override
    Collection<String> getDefaultMappingsTo(
        Map attrs,
        DefaultConfigurationSet source,
        DefaultConfigurationSet target
    ) {
        def (boolean export) = NamedParameters.checkAndGet(attrs, [['export', false]])

        return getDefaultMappingsTo(source, target, getMappingAdder(target.type as DefaultConfigurationSetType, export))
    }

    @Override
    public Collection<String> getMappingsFrom(
        Map attrs, String source
    ) {
        def (boolean export) = NamedParameters.checkAndGet(attrs, [['export', false]])

        return getDefaultMappingsTo(source, this, getMappingAdder(this, export))
    }

    private static Closure getMappingAdder(DefaultConfigurationSetType targetType, boolean export) {
        return {
            Collection<String> mappings,
            Map<String, String> binding,
            String sourceConfigurationName,
            String targetConfigurationName
             ->
            if (binding['stage'] == IMPORT_STAGE) {
                switch (targetType.class) {
                    case WindowsDynamicLibraryConfigurationSetType.class:
                    case WindowsStaticLibraryConfigurationSetType.class:
                        // Link import iff export == true
                        if (export) {
                            mappings << makeMapping(sourceConfigurationName, targetConfigurationName)
                        }
                        break;
                    case WindowsExecutableConfigurationSetType.class:
                        // Don't link import
                        break;
                    default:
                        throwForUnknownTargetType(targetType)
                        break;
                }
            } else {
                mappings << makeMapping(sourceConfigurationName, targetConfigurationName)
            }
        }
    }
}

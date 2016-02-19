package holygradle.artifacts

import groovy.transform.InheritConstructors
import holygradle.lang.NamedParameters
import org.gradle.api.artifacts.Configuration

/**
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_dll
 */
@InheritConstructors
class WindowsDynamicLibraryConfigurationSetType extends WindowsConfigurationSetType {
    @Override
    Collection<String> getDefaultMappingsTo(
        Map attrs,
        Map parameterSpecs,
        DefaultConfigurationSet source,
        DefaultConfigurationSet target
    ) {
        def (boolean export) = NamedParameters.checkAndGet(attrs, [export: false] + parameterSpecs)

        return getDefaultMappingsTo(source, target, getMappingAdder(target.typeAsDefault, export))
    }

    private Closure getMappingAdder(
        DefaultConfigurationSetType targetType,
        boolean export
    ) {
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
                        // Link import to a public config iff export == true
                        if (export) {
                            mappings << makeMapping(sourceConfigurationName, targetConfigurationName)
                        } else {
                            mappings << makeMapping(PRIVATE_BUILD_CONFIGURATION_NAME, targetConfigurationName)
                        }
                        break;
                    case WindowsExecutableConfigurationSetType.class:
                        // Don't link import because the target, being an executable or plugin DLL, should have empty
                        // import configurations.
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

package holygradle.artifacts

import groovy.transform.InheritConstructors
import org.gradle.api.artifacts.Configuration

/**
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_exe
 */
@InheritConstructors
class WindowsExecutableConfigurationSetType extends WindowsConfigurationSetType {
    @Override
    Collection<String> getDefaultMappingsTo(
        Map attrs,
        Map parameterSpecs,
        DefaultConfigurationSet source,
        DefaultConfigurationSet target
    ) {
        checkNoExport(attrs)

        return getDefaultMappingsTo(source, target, getMappingAdder(target.typeAsDefault))
    }

    @Override
    protected Collection<String> getDefaultMappingsFrom(
        Map attrs,
        Map parameterSpecs,
        Configuration source,
        DefaultConfigurationSet target
    ) {
        checkNoExport(attrs)

        return super.getDefaultMappingsFrom(attrs, parameterSpecs, source, target)
    }

    private void checkNoExport(Map attrs) {
        if (attrs.containsKey("export") && attrs["export"]) {
            throw new IllegalArgumentException(
                "The named argument 'export' cannot be true for configuration set type ${name}"
            )
        }
    }

    private static Closure getMappingAdder(
        DefaultConfigurationSetType targetType
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
                        // Don't link import to a public configuration, because the source type, being an executable or
                        // plugin DLL (that is, an instance of this class), will not be imported by anything, so its
                        // import configurations should be empty.
                        mappings << makeMapping(PRIVATE_BUILD_CONFIGURATION_NAME, targetConfigurationName)
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

package holygradle.artifacts

import org.gradle.api.artifacts.Configuration

/**
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_exe
 */
class WindowsExecutableConfigurationSetType(
        name: String,
        platforms: List<String>,
        configurations: List<String>
) : WindowsConfigurationSetType(name, platforms, configurations) {
    override fun getDefaultMappingsTo(
            attrs: Map<String, Any>,
            parameterSpecs: Map<String, Any>,
            source: DefaultConfigurationSet,
            target: DefaultConfigurationSet
    ): Collection<String> {
        checkNoExport(attrs)

        return getDefaultMappingsTo(source, target, getMappingAdder(target.typeAsDefault))
    }

    override fun getDefaultMappingsFrom(
            attrs: Map<String, Any>,
            parameterSpecs: Map<String, Any>,
            source: Configuration,
            target: DefaultConfigurationSet
    ): Collection<String> {
        checkNoExport(attrs)

        return super.getDefaultMappingsFrom(attrs, parameterSpecs, source, target)
    }

    private fun checkNoExport(attrs: Map<String, Any>) {
        if (attrs["export"] == true) {
            throw IllegalArgumentException(
                "The named argument 'export' cannot be true for configuration set type ${name}"
            )
        }
    }

    private fun getMappingAdder(
            targetType: DefaultConfigurationSetType
    ): (MutableList<String>, Map<String, String>, String, String) -> Unit {
        return {
            mappings: MutableList<String>,
            binding: Map<String, String>,
            sourceConfigurationName: String,
            targetConfigurationName: String
        ->
            if (binding["stage"] == IMPORT_STAGE) {
                when (targetType) {
                    is WindowsDynamicLibraryConfigurationSetType,
                    is WindowsStaticLibraryConfigurationSetType -> {
                        // Don't link import to a public configuration, because the source type, being an executable or
                        // plugin DLL (that is, an instance of this class), will not be imported by anything, so its
                        // import configurations should be empty.
                        mappings.add(makeMapping(PRIVATE_BUILD_CONFIGURATION_NAME, targetConfigurationName))
                    }
                    is WindowsExecutableConfigurationSetType -> {
                        // Don't link import because the target, being an executable or plugin DLL, should have empty
                        // import configurations.
                    }
                    else -> throwForUnknownTargetType(targetType)
                }
            } else {
                mappings.add(makeMapping(sourceConfigurationName, targetConfigurationName))
            }
        }
    }
}

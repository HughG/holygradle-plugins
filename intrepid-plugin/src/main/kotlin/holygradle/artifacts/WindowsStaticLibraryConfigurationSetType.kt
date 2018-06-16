package holygradle.artifacts

import holygradle.lang.NamedParameters

/**
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_lib
 */
class WindowsStaticLibraryConfigurationSetType(
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
        val export: Boolean = NamedParameters.checkAndGet(attrs, mapOf("export" to false) + parameterSpecs)[0] as Boolean

        return getDefaultMappingsTo(source, target, getMappingAdder(target.typeAsDefault, export))
    }

    private fun getMappingAdder(
            targetType: DefaultConfigurationSetType,
            export: Boolean
    ): (MutableList<String>, Map<String, String>, String, String) -> Unit {
        return {
            mappings: MutableList<String>,
            binding: Map<String, String>,
            sourceConfigurationName: String,
            targetConfigurationName: String
        ->
            if (binding["stage"] == IMPORT_STAGE) {
                when (targetType) {
                    is WindowsDynamicLibraryConfigurationSetType -> {
                        // Link import to a public config iff export == true
                        if (export) {
                            mappings.add(makeMapping(sourceConfigurationName, targetConfigurationName))
                        } else {
                            mappings.add(makeMapping(PRIVATE_BUILD_CONFIGURATION_NAME, targetConfigurationName))
                        }
                    }
                    is WindowsStaticLibraryConfigurationSetType -> {
                        // Link import, regardless of the value of export, because the LIB will be needed down-stream.
                        mappings.add(makeMapping(sourceConfigurationName, targetConfigurationName))
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

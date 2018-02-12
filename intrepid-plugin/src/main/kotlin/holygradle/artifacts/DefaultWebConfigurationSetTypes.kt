package holygradle.artifacts

/**
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_default_web_types
 */
object DefaultWebConfigurationSetTypes {
    val TYPES: Map<String, ConfigurationSetType> = listOf<ConfigurationSetType>(
            DefaultConfigurationSetType(
                    "WEB_LIB",
                    linkedMapOf(
                        DefaultConfigurationSetType.STAGE_AXIS_NAME to listOf(
                            DefaultConfigurationSetType.IMPORT_STAGE,
                            DefaultConfigurationSetType.RUNTIME_STAGE,
                            DefaultConfigurationSetType.DEBUGGING_STAGE
                        )
                    ),
                    linkedMapOf(
                        "Configuration" to DefaultVisualStudioConfigurationSetTypes.DEFAULT_CONFIGURATIONS
                    )
            )
    ).associate { it.name to it }
}

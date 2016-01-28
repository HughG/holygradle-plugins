package holygradle.artifacts

class DefaultWebConfigurationSetTypes {
    public static final Map<String, ConfigurationSetType> TYPES = [
        new DefaultConfigurationSetType(
            "WEB_LIB",
            [
                "${DefaultConfigurationSetType.STAGE_AXIS_NAME}": [
                    DefaultConfigurationSetType.IMPORT_STAGE,
                    DefaultConfigurationSetType.RUNTIME_STAGE,
                    DefaultConfigurationSetType.DEBUGGING_STAGE
                ]
            ],
            [
                Configuration: DefaultVisualStudioConfigurationSetTypes.DEFAULT_CONFIGURATIONS
            ]
        )
    ].collectEntries { type ->
        // Collect a name -> value entry
        [type.name, type]
    }
}

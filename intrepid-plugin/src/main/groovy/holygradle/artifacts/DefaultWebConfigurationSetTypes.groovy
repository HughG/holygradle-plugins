package holygradle.artifacts

class DefaultWebConfigurationSetTypes {
    public static final Map<String, ConfigurationSetType> TYPES = [
        new DefaultConfigurationSetType(
            "WEB_LIB",
            [
                stage: [
                    DefaultConfigurationSetType.IMPORT_STAGE,
                    DefaultConfigurationSetType.RUNTIME_STAGE,
                    DefaultConfigurationSetType.DEBUGGING_STAGE
                ]
            ],
            [
                configurations: DefaultVisualStudioConfigurationSetTypes.DEFAULT_CONFIGURATIONS
            ]
        )
    ].collectEntries { type ->
        // Modify the type before we collect it.
        type.commonConfigurationsSpec([stage: DefaultConfigurationSetType.IMPORT_STAGE])
        // Collect a name -> value entry
        [type.name, type]
    }
}

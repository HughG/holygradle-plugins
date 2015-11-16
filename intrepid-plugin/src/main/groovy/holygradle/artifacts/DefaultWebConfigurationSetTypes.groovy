package holygradle.artifacts

class DefaultWebConfigurationSetTypes {
    public static final Collection<ConfigurationSetType> TYPES = [
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
    ]
}

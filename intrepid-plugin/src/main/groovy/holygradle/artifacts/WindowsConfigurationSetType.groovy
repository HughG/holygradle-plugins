package holygradle.artifacts

abstract class WindowsConfigurationSetType extends DefaultConfigurationSetType {
    // Have to declare these here because, if we try to refer to the base class constants in our constructor's
    // super() call, we get a VerifyError "Expecting to find object/array on stack".  Judging by all the other
    // mentions of VerifyError online, this is probably a bug in Groovy.
    private static final String _IMPORT_STAGE = DefaultConfigurationSetType.IMPORT_STAGE
    private static final String _RUNTIME_STAGE = DefaultConfigurationSetType.RUNTIME_STAGE
    private static final String _DEBUGGING_STAGE = DefaultConfigurationSetType.DEBUGGING_STAGE

    public WindowsConfigurationSetType(String name, List<String> platforms, List<String> configurations) {
        super(
            name,
            [
                stage: [_IMPORT_STAGE, _RUNTIME_STAGE, _DEBUGGING_STAGE]
            ],
            [
                platforms: platforms,
                configurations: configurations
            ]
        )
        commonConfigurationsSpec([stage: _IMPORT_STAGE])
    }

    protected static Closure getMappingFromSingleConfigurationAdder(
        boolean export
    ) {
        return {
            Collection<String> mappings,
            Map<String, String> binding,
            String sourceConfigurationName,
            String targetConfigurationName
             ->
            if (binding['stage'] == IMPORT_STAGE) {
                // Link import to a public config iff export == true
                String sourceName = export ? sourceConfigurationName : PRIVATE_BUILD_CONFIGURATION_NAME
                mappings << makeMapping(sourceName, targetConfigurationName)
            } else {
                mappings << makeMapping(sourceConfigurationName, targetConfigurationName)
            }
        }
    }
}

package holygradle.artifacts

/**
 * Base class for types which relate to C++-style projects using a Windows compiler such as Visual Studio.  This class
 * defines specific required and optional axes, and sets up a specific set of "{@code _common}" configurations.
 *
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_default_visual_studio_types
 */
abstract class WindowsConfigurationSetType extends DefaultConfigurationSetType {
    // Have to declare these here because, if we try to refer to the base class constants in our constructor's
    // super() call, we get a VerifyError "Expecting to find object/array on stack".  Judging by all the other
    // mentions of VerifyError online, this is probably a bug in Groovy.
    private static final String _STAGE_AXIS_NAME = DefaultConfigurationSetType.STAGE_AXIS_NAME
    private static final String _IMPORT_STAGE = DefaultConfigurationSetType.IMPORT_STAGE
    private static final String _RUNTIME_STAGE = DefaultConfigurationSetType.RUNTIME_STAGE
    private static final String _DEBUGGING_STAGE = DefaultConfigurationSetType.DEBUGGING_STAGE

    public WindowsConfigurationSetType(String name, List<String> platforms, List<String> configurations) {
        super(
            name,
            [
                "${_STAGE_AXIS_NAME}": [_IMPORT_STAGE, _RUNTIME_STAGE, _DEBUGGING_STAGE]
            ],
            [
                Platform: platforms,
                Configuration: configurations
            ]
        )
        commonConfigurationsSpec([stage: _IMPORT_STAGE])
    }
}

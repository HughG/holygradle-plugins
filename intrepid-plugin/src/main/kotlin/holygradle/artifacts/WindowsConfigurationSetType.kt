package holygradle.artifacts

/**
 * Base class for types which relate to C++-style projects using a Windows compiler such as Visual Studio.  This class
 * defines specific required and optional axes, and sets up a specific set of "{@code _common}" configurations.
 *
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_default_visual_studio_types
 */
abstract class WindowsConfigurationSetType(
        name: String,
        platforms: List<String>,
        configurations: List<String>
) : DefaultConfigurationSetType(
        name,
        linkedMapOf(STAGE_AXIS_NAME to listOf(IMPORT_STAGE, RUNTIME_STAGE, DEBUGGING_STAGE)),
        linkedMapOf(
            "Platform" to platforms,
            "Configuration" to configurations
        )
    )
{
    init {
        commonConfigurationsSpec(linkedMapOf("stage" to IMPORT_STAGE))
    }
}

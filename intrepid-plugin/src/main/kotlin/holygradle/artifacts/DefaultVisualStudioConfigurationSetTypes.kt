package holygradle.artifacts

/**
 * @see http://holygradle.bitbucket.org/plugin-intrepid.html#_default_visual_studio_types
 */
object DefaultVisualStudioConfigurationSetTypes {
    private val VS_PLATFORM_X64 = "x64"
    private val VS_PLATFORM_WIN32 = "Win32"
    private val VS_CONFIGURATION_RELEASE = "Release"
    private val VS_CONFIGURATION_DEBUG = "Debug"

    val DEFAULT_PLATFORMS: List<String> = listOf(VS_PLATFORM_X64, VS_PLATFORM_WIN32)
    val DEFAULT_CONFIGURATIONS: List<String> = listOf(VS_CONFIGURATION_RELEASE, VS_CONFIGURATION_DEBUG)

    val TYPES: Map<String, ConfigurationSetType> = listOf<ConfigurationSetType>(
        WindowsDynamicLibraryConfigurationSetType(
                "DLL_64",
                listOf(VS_PLATFORM_X64),
                DEFAULT_CONFIGURATIONS
        ),

        WindowsDynamicLibraryConfigurationSetType(
                "DLL_64_RELEASE",
                listOf(VS_PLATFORM_X64),
                listOf(VS_CONFIGURATION_RELEASE)
        ),

        WindowsDynamicLibraryConfigurationSetType(
                "DLL",
                DEFAULT_PLATFORMS,
                DEFAULT_CONFIGURATIONS
        ),

        WindowsDynamicLibraryConfigurationSetType(
                "DLL_RELEASE",
                DEFAULT_PLATFORMS,
                listOf(VS_CONFIGURATION_RELEASE)
        ),

        WindowsStaticLibraryConfigurationSetType(
                "LIB_64",
                listOf(VS_PLATFORM_X64),
                DEFAULT_CONFIGURATIONS
        ),

        WindowsStaticLibraryConfigurationSetType(
                "LIB_64_RELEASE",
                listOf(VS_PLATFORM_X64),
                listOf(VS_CONFIGURATION_RELEASE)
        ),

        WindowsStaticLibraryConfigurationSetType(
                "LIB",
                DEFAULT_PLATFORMS,
                DEFAULT_CONFIGURATIONS
        ),

        WindowsStaticLibraryConfigurationSetType(
                "LIB_RELEASE",
                DEFAULT_PLATFORMS,
                listOf(VS_CONFIGURATION_RELEASE)
        ),

        WindowsExecutableConfigurationSetType(
                "EXE_64",
                listOf(VS_PLATFORM_X64),
                DEFAULT_CONFIGURATIONS
        ),

        WindowsExecutableConfigurationSetType(
            "EXE_64_RELEASE",
                listOf(VS_PLATFORM_X64),
                listOf(VS_CONFIGURATION_RELEASE)
        ),

        WindowsExecutableConfigurationSetType(
                "EXE",
                DEFAULT_PLATFORMS,
                DEFAULT_CONFIGURATIONS
        ),

        WindowsExecutableConfigurationSetType(
                "EXE_RELEASE",
                DEFAULT_PLATFORMS,
                listOf(VS_CONFIGURATION_RELEASE)
        )
    ).associate { it.name to it }
}

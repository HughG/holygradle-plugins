package holygradle.artifacts

class DefaultVisualStudioConfigurationSetTypes {
    private static final String VS_PLATFORM_X64 = "x64"
    private static final String VS_PLATFORM_WIN32 = "Win32"
    private static final String VS_CONFIGURATION_RELEASE = "Release"
    private static final String VS_CONFIGURATION_DEBUG = "Debug"

    public static final List<String> DEFAULT_PLATFORMS = [VS_PLATFORM_X64, VS_PLATFORM_WIN32]
    public static final List<String> DEFAULT_CONFIGURATIONS = [VS_CONFIGURATION_RELEASE, VS_CONFIGURATION_DEBUG]

    public static final Collection<ConfigurationSetType> TYPES = [
        new WindowsDynamicLibraryConfigurationSetType(
            "DLL_64",
            [VS_PLATFORM_X64],
            DEFAULT_CONFIGURATIONS
        ),

        new WindowsDynamicLibraryConfigurationSetType(
            "DLL_64_RELEASE",
            [VS_PLATFORM_X64],
            [VS_CONFIGURATION_RELEASE]
        ),

        new WindowsDynamicLibraryConfigurationSetType(
            "DLL",
            DEFAULT_PLATFORMS,
            DEFAULT_CONFIGURATIONS
        ),

        new WindowsDynamicLibraryConfigurationSetType(
            "DLL_RELEASE",
            DEFAULT_PLATFORMS,
            [VS_CONFIGURATION_RELEASE]
        ),

        new WindowsStaticLibraryConfigurationSetType(
            "LIB_64",
            [VS_PLATFORM_X64],
            DEFAULT_CONFIGURATIONS
        ),

        new WindowsStaticLibraryConfigurationSetType(
            "LIB_64_RELEASE",
            [VS_PLATFORM_X64],
            [VS_CONFIGURATION_RELEASE]
        ),

        new WindowsStaticLibraryConfigurationSetType(
            "LIB",
            DEFAULT_PLATFORMS,
            DEFAULT_CONFIGURATIONS
        ),

        new WindowsStaticLibraryConfigurationSetType(
            "LIB_RELEASE",
            DEFAULT_PLATFORMS,
            [VS_CONFIGURATION_RELEASE]
        ),

        new WindowsExecutableConfigurationSetType(
            "EXE_64",
            [VS_PLATFORM_X64],
            DEFAULT_CONFIGURATIONS
        ),

        new WindowsExecutableConfigurationSetType(
            "EXE_64_RELEASE",
            [VS_PLATFORM_X64],
            [VS_CONFIGURATION_RELEASE]
        ),

        new WindowsExecutableConfigurationSetType(
            "EXE",
            DEFAULT_PLATFORMS,
            DEFAULT_CONFIGURATIONS
        ),

        new WindowsExecutableConfigurationSetType(
            "EXE_RELEASE",
            DEFAULT_PLATFORMS,
            [VS_CONFIGURATION_RELEASE]
        ),
    ]
}

package holygradle.artifacts

import org.junit.Test

class WindowsConfigurationSetTypeTest {
    @Test
    public void testNames() {
        ConfigurationSetType dllType = new WindowsDynamicLibraryConfigurationSetType("testDllType", ["x64", "Win32"], ["Release", "Debug"])
        println dllType.requiredAxes
        println dllType.optionalAxes

        ConfigurationSetType libType = new WindowsStaticLibraryConfigurationSetType("testLibType", ["x64", "Win32"], ["Release", "Debug"])
        println libType.requiredAxes
        println libType.optionalAxes

        DefaultConfigurationSet dllConfigurationSet = new DefaultConfigurationSet("testDllConfSet")
        dllConfigurationSet.type = dllType
        DefaultConfigurationSet libConfigurationSet = new DefaultConfigurationSet("testLibConfSet")
        libConfigurationSet.type = libType

        println dllType.getMappingsTo(dllConfigurationSet, libConfigurationSet)
    }
}

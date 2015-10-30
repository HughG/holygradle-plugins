package holygradle.artifacts

import holygradle.test.AbstractHolyGradleTest
import org.junit.Test

class DefaultConfigurationSetTest extends AbstractHolyGradleTest {
    class TestConfigurationSetType extends WindowsConfigurationSetType {

        public TestConfigurationSetType(
            String name,
            List<String> platforms,
            List<String> configurations
        ) {
            super(name, platforms, configurations)
        }

        @Override
        protected Collection<String> getDefaultMappingsTo(
            Map attrs,
            DefaultConfigurationSet source,
            DefaultConfigurationSet target
        ) {
            throw new UnsupportedOperationException()
        }
    }

    @Test
    public void testNames() {
        ConfigurationSetType type = new TestConfigurationSetType("testType", ["x64", "Win32"], ["Release", "Debug"])
        println type.requiredAxes
        println type.optionalAxes

        DefaultConfigurationSet configurationSet = new DefaultConfigurationSet("testConfSet")
        configurationSet.type(type)

        println configurationSet.configurationNames.values().join('\n')
        println configurationSet.configurationNames
    }
}

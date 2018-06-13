package holygradle.artifacts

import holygradle.test.AbstractHolyGradleTest
import holygradle.test.AssertHelper
import org.gradle.api.artifacts.Configuration
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class DefaultConfigurationSetTest extends AbstractHolyGradleTest {
    @Rule public ExpectedException thrown = ExpectedException.none();

    public static final ArrayList<String> ASPECTS = DefaultConfigurationSetTypeTest.ASPECTS
    public static final ArrayList<String> COLOURS = DefaultConfigurationSetTypeTest.COLOURS
    // It's not important what this order is, but it is important that it's stable -- i.e., that the methods return
    // LinkedList-based lists and maps.
    public static final ArrayList<String> COMMON_CONFIGURATION_NAMES = DefaultConfigurationSetTypeTest.COMMON_CONFIGURATION_NAMES
    public static final ArrayList<String> MAIN_CONFIGURATION_NAMES = DefaultConfigurationSetTypeTest.MAIN_CONFIGURATION_NAMES
    public static final ArrayList<String> COMMON_AND_MAIN_CONFIGURATION_NAMES =
        COMMON_CONFIGURATION_NAMES + MAIN_CONFIGURATION_NAMES

    private static class OtherConfigurationSetType implements ConfigurationSetType {
        @Override
        Collection<String> getMappingsTo(Map attrs, ConfigurationSet source, ConfigurationSet target) {
            return null
        }

        @Override
        Collection<String> getMappingsTo(ConfigurationSet source, ConfigurationSet target) {
            return null
        }

        @Override
        Collection<String> getMappingsTo(Map attrs, ConfigurationSet source, ConfigurationSetType targetType) {
            return null
        }

        @Override
        Collection<String> getMappingsTo(ConfigurationSet source, ConfigurationSetType targetType) {
            return null
        }

        @Override
        Collection<String> getMappingsFrom(Map attrs, Configuration source) {
            return null
        }

        @Override
        Collection<String> getMappingsFrom(Configuration source) {
            return null
        }

        @Override
        Collection<String> getMappingsFrom(Map attrs, Configuration source, ConfigurationSet target) {
            return null
        }

        @Override
        Collection<String> getMappingsFrom(Configuration source, ConfigurationSet target) {
            return null
        }

        @Override
        String getName() {
            return null
        }
    }

    @Test
    public void testBasicInit() {
        DefaultConfigurationSetType type =
            new DefaultConfigurationSetTypeTest.TestConfigurationSetType("type", ASPECTS, COLOURS)

        DefaultConfigurationSet set = new DefaultConfigurationSet("testConfSet")
        Assert.assertEquals("Check name is initialised", "testConfSet", set.name)

        AssertHelper.assertThrows("Check type is not initialised", Exception.class) { set.type }
        set.type(type)
        Assert.assertEquals("Check type is initialised", type, set.type)
        Assert.assertEquals("Check type is available as DefaultConfigurationSetType", type, set.typeAsDefault)
        Assert.assertEquals("Check axes match type axes", type.axes, set.axes)

        Assert.assertEquals("Check prefix is not initialised", "", set.prefix)
        set.prefix("prefix")
        Assert.assertEquals("Check prefix is initialised", "prefix", set.prefix)
    }

    @Test
    public void testSetType() {
        DefaultConfigurationSetType type =
            new DefaultConfigurationSetTypeTest.TestConfigurationSetType("type", ASPECTS, COLOURS)
        DefaultConfigurationSetType type2 =
            new DefaultConfigurationSetTypeTest.TestConfigurationSetType("type2", ASPECTS, COLOURS)

        DefaultConfigurationSet set = new DefaultConfigurationSet("testConfSet")
        set.type(type)
        Assert.assertEquals("Check type is initialised", type, set.type)
        set.type(type2)
        Assert.assertEquals("Check type can be changed", type2, set.type)
        // And now check that we can't change it back after we got the configuration names.
        set.configurationNames
        thrown.expect(RuntimeException)
        set.type(type)
    }

    @Test
    public void testSetPrefix() {
        DefaultConfigurationSetType type =
            new DefaultConfigurationSetTypeTest.TestConfigurationSetType("type", ASPECTS, COLOURS)

        DefaultConfigurationSet set = new DefaultConfigurationSet("testConfSet")
        set.type(type)
        set.prefix("prefix")
        Assert.assertEquals("Check prefix is initialised", "prefix", set.prefix)
        set.prefix("affix")
        Assert.assertEquals("Check type can be changed", "affix", set.prefix)
        // And now check that we can't change it back after we got the configuration names
        set.configurationNames
        thrown.expect(RuntimeException)
        set.prefix("prefix")
    }

    @Test
    public void testOtherType() {
        ConfigurationSetType type = new OtherConfigurationSetType()

        DefaultConfigurationSet set = new DefaultConfigurationSet("testConfSet")
        thrown.expect(RuntimeException)
        set.type(type)
    }

    @Test
    public void testNamesWithoutPrefix() {
        DefaultConfigurationSetType type =
            new DefaultConfigurationSetTypeTest.TestConfigurationSetType("type", ASPECTS, COLOURS)
        DefaultConfigurationSet set = new DefaultConfigurationSet("testConfSet")
        set.type(type)

        Assert.assertEquals(
            COMMON_AND_MAIN_CONFIGURATION_NAMES,
            set.configurationNames
        )
        Assert.assertEquals(
            set.configurationNames,
            set.configurationNamesMap.values().toList()
        )
    }

    @Test
    public void testNamesWithPrefix() {
        DefaultConfigurationSetType theType =
            new DefaultConfigurationSetTypeTest.TestConfigurationSetType("type", ASPECTS, COLOURS)
        DefaultConfigurationSet set = new DefaultConfigurationSet("testConfSet")
        set.with {
            type theType
            prefix "prefix"
        }

        Assert.assertEquals(
            // We need an extra toString to make sure all GStrings are converted to Strings.
            (COMMON_AND_MAIN_CONFIGURATION_NAMES.collect { "prefix_${it}" })*.toString(),
            set.configurationNames
        )
        Assert.assertEquals(
            set.configurationNames,
            set.configurationNamesMap.values().toList()
        )
    }

}

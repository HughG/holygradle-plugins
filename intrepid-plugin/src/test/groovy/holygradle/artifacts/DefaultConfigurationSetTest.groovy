package holygradle.artifacts

import holygradle.test.AbstractHolyGradleTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class DefaultConfigurationSetTest extends AbstractHolyGradleTest {
    @Rule public ExpectedException thrown = ExpectedException.none();

    public static final ArrayList<String> ASPECTS = ["main", "test"]
    public static final ArrayList<String> COLOURS = ["orange", "purple"]
    public static final LinkedHashMap<String, ArrayList<String>> REQUIRED_AXES = [
        "aspect": ASPECTS,
        "lifecycleStage": ["make", "use"]
    ]
    public static final LinkedHashMap<String, ArrayList<String>> OPTIONAL_AXES = [
        "colour": COLOURS,
        "size": ["medium", "large"]
    ]
    // It's not important what this order is, but it is important that it's stable -- i.e., that the methods return
    // LinkedList-based lists and maps.
    public static final ArrayList<String> COMMON_CONFIGURATION_NAMES = [
        "test_use_common",
        "main_use_common",
        "test_make_common",
        "main_make_common",
    ]
    public static final ArrayList<String> MAIN_CONFIGURATION_NAMES = [
        "test_use_purple_large",
        "main_use_purple_large",
        "test_make_purple_large",
        "main_make_purple_large",
        "test_use_orange_large",
        "main_use_orange_large",
        "test_make_orange_large",
        "main_make_orange_large",
        "test_use_purple_medium",
        "main_use_purple_medium",
        "test_make_purple_medium",
        "main_make_purple_medium",
        "test_use_orange_medium",
        "main_use_orange_medium",
        "test_make_orange_medium",
        "main_make_orange_medium",
    ]
    public static final ArrayList<String> COMMON_AND_MAIN_CONFIGURATION_NAMES =
        COMMON_CONFIGURATION_NAMES + MAIN_CONFIGURATION_NAMES
    public static final LinkedHashMap<String, String> EXTRA_ATTRS = [a: "b", c: "d"]
    public static final ArrayList<String> EXTRA_ATTRS_MAPPINGS = ["a->b", "c->d"]

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
        Collection<String> getMappingsFrom(Map attrs, String source) {
            return null
        }

        @Override
        Collection<String> getMappingsFrom(String source) {
            return null
        }

        @Override
        Collection<String> getMappingsFrom(Map attrs, String source, ConfigurationSet target) {
            return null
        }

        @Override
        Collection<String> getMappingsFrom(String source, ConfigurationSet target) {
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

        Assert.assertNull("Check type is not initialised", set.type)
        set.type(type)
        Assert.assertEquals("Check type is initialised", type, set.type)
        Assert.assertEquals("Check type is available as DefaultConfigurationSetType", type, set.typeAsDefault)

        Assert.assertNull("Check prefix is not initialised", set.prefix)
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
            set.configurationNames.values().toList()
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
            set.configurationNames.values().toList()
        )
    }

}

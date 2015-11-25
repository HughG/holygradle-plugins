package holygradle.artifacts

import holygradle.test.AbstractHolyGradleTest
import org.junit.Assert
import org.junit.Test

class DefaultConfigurationSetTypeTest extends AbstractHolyGradleTest {
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


    public static class TestConfigurationSetType extends DefaultConfigurationSetType {
        public TestConfigurationSetType(
            String name,
            List<String> aspects,
            List<String> colours
        ) {
            super(
                name,
                [
                    aspect: aspects,
                    lifecycleStage: ["make", "use"]
                ],
                [
                    colour: colours,
                    size: ["medium", "large"]
                ]
            )
        }

        @Override
        protected Collection<String> getDefaultMappingsTo(
            Map attrs,
            DefaultConfigurationSet source,
            DefaultConfigurationSet target
        ) {
            // An unrealistic implementation of getDefaultMappingsTo which just adds attrs as extra mappings.
            Collection<String> mappings = super.getDefaultMappingsTo(attrs, source, target)
            return attrs.collect(mappings) { k, v -> "${k}->${v}".toString() }
        }

        @Override
        protected Collection<String> getDefaultMappingsFrom(Map attrs, String source, DefaultConfigurationSet target) {
            // An unrealistic implementation of getDefaultMappingsTo which just adds attrs as extra mappings.
            Collection<String> mappings = super.getDefaultMappingsFrom(attrs, source, target)
            return attrs.collect(mappings) { k, v -> "${k}->${v}".toString() }
        }
    }

    @Test
    public void testSetAxesInConstructor() {
        ConfigurationSetType type = new TestConfigurationSetType("type", ASPECTS, COLOURS)
        Assert.assertEquals("name", "type", type.name)
        Assert.assertEquals("requiredAxes", REQUIRED_AXES, type.requiredAxes)
        Assert.assertEquals("optionalAxes", OPTIONAL_AXES, type.optionalAxes)
    }

    @Test
    public void testSetAxesWithMethods() {
        ConfigurationSetType type = new DefaultConfigurationSetType("type")
        type.with {
            requiredAxes(
                aspect: ASPECTS,
                lifecycleStage: ["make", "use"]
            )
            optionalAxes = [
                colour: COLOURS,
                size: ["medium", "large"]
            ]
        }
        Assert.assertEquals("name", "type", type.name)
        Assert.assertEquals("requiredAxes", REQUIRED_AXES, type.requiredAxes)
        Assert.assertEquals("optionalAxes", OPTIONAL_AXES, type.optionalAxes)
    }

    @Test
    public void testWithPrefix() {
        ConfigurationSetType type = new TestConfigurationSetType("type", ASPECTS, COLOURS)

        DefaultConfigurationSet set = type.withPrefix("foo")

        Assert.assertEquals(
            // We need an extra toString to make sure all GStrings are converted to Strings.
            (COMMON_AND_MAIN_CONFIGURATION_NAMES.collect { "foo_${it}" })*.toString(),
            set.configurationNames.values().toList()
        )
    }

    @Test
    public void testMappingSetToSet() {
        ConfigurationSetType type = new TestConfigurationSetType("type", ASPECTS, COLOURS)

        DefaultConfigurationSet fromSet = type.withPrefix("foo")

        DefaultConfigurationSet toSet = new DefaultConfigurationSet("testConfSet")
        toSet.type(type)

        Assert.assertEquals(
            // We need an extra toString to make sure all GStrings are converted to Strings.
            ((MAIN_CONFIGURATION_NAMES.collect { "foo_${it}->${it}" }) + EXTRA_ATTRS_MAPPINGS)*.toString(),
            type.getMappingsTo(EXTRA_ATTRS, fromSet, toSet)
        )
    }

    @Test
    public void testMappingSetToSetType() {
        ConfigurationSetType type = new TestConfigurationSetType("type", ASPECTS, COLOURS)

        DefaultConfigurationSet fromSet = type.withPrefix("foo")

        Assert.assertEquals(
            // We need an extra toString to make sure all GStrings are converted to Strings.
            ((MAIN_CONFIGURATION_NAMES.collect { "foo_${it}->${it}" }) + EXTRA_ATTRS_MAPPINGS)*.toString(),
            type.getMappingsTo(EXTRA_ATTRS, fromSet, type)
        )
    }

    @Test
    public void testMappingFromConfiguration() {
        ConfigurationSetType type = new TestConfigurationSetType("type", ASPECTS, COLOURS)

        Assert.assertEquals(
            // We need an extra toString to make sure all GStrings are converted to Strings.
            ((MAIN_CONFIGURATION_NAMES.collect { "conf->${it}" }) + EXTRA_ATTRS_MAPPINGS)*.toString(),
            type.getMappingsFrom(EXTRA_ATTRS, "conf")
        )
    }

    @Test
    public void testMappingFromConfigurationToSet() {
        ConfigurationSetType type = new TestConfigurationSetType("type", ASPECTS, COLOURS)

        DefaultConfigurationSet toSet = type.withPrefix("foo")

        Assert.assertEquals(
            // We need an extra toString to make sure all GStrings are converted to Strings.
            ((MAIN_CONFIGURATION_NAMES.collect { "conf->foo_${it}" }) + EXTRA_ATTRS_MAPPINGS)*.toString(),
            type.getMappingsFrom(EXTRA_ATTRS, "conf", toSet)
        )
    }
}

package holygradle.artifacts

import com.google.common.collect.Sets
import holygradle.test.AbstractHolyGradleTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Regression test for (some of) the default configuration set types.
 *
 * <p><ul>
 *     <li>Configurations ending in "_common" should never be mapped (as they'll be pulled in through "extendsFrom").</li>
 *     <li>All mappings should connect all (non-common) "runtime_" and "debugging_" configurations.</li>
 *     <li>Mapping from EXE to anything should never map the "import_" configurations.</li>
 *     <li>Mapping from DLL to anything should map the "import_" configurations if and only if the "export" flag is true.</li>
 *     <li>
 *         Mapping from LIB to anything should map the "import_" configurations if and only if the "export" flag is
 *         true, <em>except</em> that mapping from LIB to LIB should always map the "import_" configurations.  This is
 *         because if a static library A has a dependency on another static library B, then any DLL or EXE C which wants
 *         to link ("import_") A will also have to link B, even if it doesn't need the headers for B.
 *     </li>
 * </ul></p>
 */
@RunWith(Parameterized.class)
class WindowsConfigurationSetTypeTest extends AbstractHolyGradleTest {
    private static final Map<String, ConfigurationSetType> WEB_TYPES = DefaultWebConfigurationSetTypes.TYPES
    private static final Map<String, ConfigurationSetType> VS_TYPES = DefaultVisualStudioConfigurationSetTypes.TYPES

    @Parameterized.Parameters(name = "{index}: from {0} to {2}; export: {4}")
    public static Collection<Object[]> data() {
        def vsTypes = [VS_TYPES["LIB"], VS_TYPES["DLL"], VS_TYPES["EXE"]].toSet()
        def vsTypeCombinations = Sets.cartesianProduct([
            vsTypes, vsTypes, [false, true].toSet()
        ])
        return (
            [
                [WEB_TYPES["WEB_LIB"], WEB_TYPES["WEB_LIB"], null],
            ] +
                vsTypeCombinations
        ).collect { from_to_export ->
            // Add in the names, purely for ease of test naming in @Parameterized.Parameters
            def (from, to, export) = from_to_export
            [from.name, from, to.name, to, export]
        }*.toArray()
    }

    private final String fromTypeName
    private final ConfigurationSetType fromType
    private final String toTypeName
    private final ConfigurationSetType toType
    private final Boolean isExport // yes, no, or "not applicable"

    WindowsConfigurationSetTypeTest(
        String fromTypeName,
        ConfigurationSetType fromType,
        String toTypeName,
        ConfigurationSetType toType,
        Boolean isExport
    ) {
        this.toTypeName = toTypeName
        this.fromTypeName = fromTypeName
        this.toType = toType
        this.fromType = fromType
        this.isExport = isExport
    }

    @Test
    public void testConfigurationSetType() {
        String fileName = "${fromType.name}_to_${toType.name}"
        if (isExport != null) {
            fileName += "_" + (isExport ? "export" : "non-export")
        }
        DefaultConfigurationSet fromSet = new DefaultConfigurationSet(fileName)
        fromSet.type(fromType)
        Collection<String> mappings = fromSet.type.getMappingsTo(fromSet, toType, export: isExport)

        File regTestFile = regression.getTestFile(fileName)
        regTestFile.withPrintWriter { w ->
            mappings.each { m -> w.println(m) }
        }
        regression.checkForRegression(fileName)
    }
}

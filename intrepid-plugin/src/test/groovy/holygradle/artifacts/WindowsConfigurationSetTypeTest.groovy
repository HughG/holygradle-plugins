package holygradle.artifacts

import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.testfixtures.ProjectBuilder
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

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return (WEB_TYPES.values() + VS_TYPES.values()).collect { [it.name, it].toArray() }
    }

    private final String typeName
    private final ConfigurationSetType type

    WindowsConfigurationSetTypeTest(
        String typeName,
        ConfigurationSetType type
    ) {
        this.typeName = typeName
        this.type = type
    }

    @Test
    public void testConfigurationSetType() {
        String fileName = typeName
        DefaultConfigurationSet fromSet = new DefaultConfigurationSet(fileName)
        fromSet.type(type)
        Map<Map<String,String>, String> names = fromSet.configurationNames
        Project project = ProjectBuilder.builder().build()
        Map<Map<String, String>, Configuration> configurations = fromSet.getConfigurations(project)
        Configuration exampleSourceConf = project.configurations.create("exampleSourceConf")
        Collection<String> mappingsExport = fromSet.type.getMappingsFrom(exampleSourceConf, export: true)
        Collection<String> mappingsNonExport = fromSet.type.getMappingsFrom(exampleSourceConf)

        File regTestFile = regression.getTestFile(fileName)
        regTestFile.withPrintWriter { w ->
            names.each { binding, name ->
                Configuration configuration = configurations[binding]
                w.println("${binding} ==> ${name} (visible = ${configuration.visible}) extendsFrom ${configuration.extendsFrom*.name}")
                w.println()
                w.println("Mappings from a single config (with export)")
                mappingsExport.each { m -> w.println(m) }
                w.println()
                w.println("Mappings from a single config (non-export)")
                mappingsNonExport.each { m -> w.println(m) }
                w.println()
            }
        }
        regression.checkForRegression(fileName)
    }
}

package holygradle.publishing

import com.google.common.collect.Sets
import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static org.junit.Assert.assertTrue

@RunWith(Parameterized.class)
class DefaultPublishPackagesExtensionIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Parameterized.Parameters(name = "{index}: {0} relativePath = {1}")
    public static Collection<Object[]> data() {
        return Sets.cartesianProduct([
            ["projectA", "projectB"].toSet(),
            [false, true].toSet()
        ])*.toArray()
    }

    private final String testProjectDirName
    private final boolean addRelativePaths

    DefaultPublishPackagesExtensionIntegrationTest(
        String testProjectDirName,
        boolean addRelativePaths
    ) {

        this.addRelativePaths = addRelativePaths
        this.testProjectDirName = testProjectDirName
    }

    @Test
    public void testDependenciesInIvyXml() {
        String regressionFileNameBase = "${testProjectDirName}_${addRelativePaths ? 'relPath_' : ''}IvyXml"
        File projectDir = new File(getTestDir(), testProjectDirName)
        File publicationsDir = new File(projectDir, "build/publications")
        FileHelper.ensureDeleteDirRecursive(publicationsDir)
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("generateIvyModuleDescriptor")
            launcher.addArguments("--info", "-PaddRelativePaths=${addRelativePaths}")
        }

        File ivyXml = new File(publicationsDir, "ivy/ivy.xml")
        assertTrue(ivyXml.exists())
        File regTestFile = regression.getTestFile(regressionFileNameBase)
        // Filter out the <info> tag which has a timestamp, therefore breaks the test
        regTestFile.withPrintWriter { w ->
            ivyXml.eachLine { l ->
                if (!l.contains("publication=")) {
                    // Filter out the absolutePath tag which will change based on the checkout location
                    w.println(l.replaceAll(/holygradle:absolutePath="(.*?)" /, ""))
                }
            }
        }
        regression.checkForRegression(regressionFileNameBase)
    }

}
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
    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Sets.cartesianProduct([
            ["projectA", "projectB"].toSet(),
        ])*.toArray()
    }

    private final String testProjectDirName

    DefaultPublishPackagesExtensionIntegrationTest(
        String testProjectDirName
    ) {
        this.testProjectDirName = testProjectDirName
    }

    @Test
    public void testDependenciesInIvyXml() {
        String regressionFileNameBase = "${testProjectDirName}_IvyXml"
        File projectDir = new File(getTestDir(), testProjectDirName)
        File publicationsDir = new File(projectDir, "build/publications")
        FileHelper.ensureDeleteDirRecursive(publicationsDir)
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("generateDescriptorFileForIvyPublication")
            launcher.addArguments("--info")
        }

        File ivyXml = new File(publicationsDir, "ivy/ivy.xml")
        assertTrue(ivyXml.exists())
        File regTestFile = regression.getTestFile(regressionFileNameBase)
        // Filter out the <info> tag which has a timestamp, therefore breaks the test
        regTestFile.withPrintWriter { w ->
            ivyXml.eachLine { l ->
                if (!l.contains("publication=")) w.println(l)
            }
        }
        regression.checkForRegression(regressionFileNameBase)
    }

}
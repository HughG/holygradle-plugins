package holygradle.publishing

import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

import static org.junit.Assert.assertTrue

class DefaultPublishPackagesExtensionIntegrationTest extends AbstractHolyGradleIntegrationTest {
    
    @Test
    public void testDependenciesInIvyXml() {
    
        ["projectA", "projectB"].each { String testProjectDirName ->
    
            File projectDir = new File(getTestDir(), testProjectDirName)
            File publicationsDir = new File(projectDir, "build/publications")
            FileHelper.ensureDeleteDirRecursive(publicationsDir)
            invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
                launcher.forTasks("generateIvyModuleDescriptor")
                launcher.addArguments("--info")
            }
            
            File ivyXml = new File(publicationsDir, "ivy/ivy.xml")
            assertTrue(ivyXml.exists())
            File regTestFile = regression.getTestFile("${testProjectDirName}_IvyXml")
            // Filter out the <info> tag which has a timestamp, therefore breaks the test
            regTestFile.withPrintWriter { w ->
                ivyXml.eachLine { l ->
                    if (!l.contains("publication=")) {
                        // Filter out the absolutePath tag which will change based on the checkout location
                        w.println(l.replaceAll(/holygradle:absolutePath="(.*?)" /, ""))
                    }
                }
            }
            regression.checkForRegression("${testProjectDirName}_IvyXml")
        }
    }

}
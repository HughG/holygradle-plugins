package holygradle.publishing

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.assertTrue

class DefaultPublishPackagesExtensionIntegrationTest extends AbstractHolyGradleIntegrationTest {
    
    @Test
    public void testDependenciesInIvyXml() {
    
        ["projectA", "projectB"].each { String testProjectDirName ->
    
            File projectDir = new File(getTestDir(), testProjectDirName)
            File publicationsDir = new File(projectDir, "build/publications")
            
            if (publicationsDir.exists()) {
                publicationsDir.deleteDir()
            }
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
                    if (!l.contains("publication=")) w.println(l)
                }
            }
            regression.checkForRegression("${testProjectDirName}_IvyXml")
        }
    }

}
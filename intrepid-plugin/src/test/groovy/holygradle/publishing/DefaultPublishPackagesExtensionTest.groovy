package holygradle.publishing

import holygradle.publishing.DefaultPublishPackagesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyModuleDescriptor
import org.gradle.api.publish.ivy.IvyPublication
import holygradle.test.TestBase
import holygradle.test.WrapperBuildLauncher
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.tooling.BuildLauncher
import org.junit.Test

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

class DefaultPublishPackagesExtensionTest extends TestBase {
    @Test
    public void testDependenciesInIvyXml() {
        File projectDir = new File(getTestDir(), "projectA")
        File publicationsDir = new File(projectDir, "build/publications")
        
        if (publicationsDir.exists()) {
            publicationsDir.deleteDir()
        }
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("generateIvyModuleDescriptor")
            launcher.withArguments("--info")
        }
        
        File ivyXml = new File(publicationsDir, "ivy/ivy.xml")
        assertTrue(ivyXml.exists())
        File regTestFile = regression.getTestFile("ProjectA_IvyXml")
        // Filter out the <info> tag which has a timestamp, therefore breaks the test
        regTestFile.withPrintWriter { w ->
            ivyXml.eachLine { l ->
                if (!l.contains("publication=")) w.println(l)
            }
        }
        regression.checkForRegression("ProjectA_IvyXml")
    }
        
}

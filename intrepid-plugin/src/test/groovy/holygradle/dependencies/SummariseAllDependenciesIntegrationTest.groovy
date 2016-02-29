package holygradle.dependencies

import com.google.common.io.Files
import holygradle.custom_gradle.util.Symlink
import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.apache.commons.io.FileUtils
import org.junit.Test

import static org.junit.Assert.assertTrue

/**
 * Integration tests for {@link holygradle.dependencies.SummariseAllDependenciesTask}
 */
class SummariseAllDependenciesIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void test() {
        File templateDir = new File(getTestDir(), "projectAIn")
        File projectDir = new File(getTestDir(), "projectA")

        File outputFile = new File(projectDir, "all-dependencies.xml")

        if (projectDir.exists()) {
            assertTrue("Removed existing ${projectDir}", projectDir.deleteDir())
        }

        FileUtils.copyDirectory(templateDir, projectDir)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("summariseAllDependencies")
        }

        assertTrue(outputFile.exists())
        println(regression.getOkFile("AllDependenciesXml"))
        Files.copy(outputFile, regression.getTestFile("AllDependenciesXml"))

        regression.checkForRegression("AllDependenciesXml")
    }
}

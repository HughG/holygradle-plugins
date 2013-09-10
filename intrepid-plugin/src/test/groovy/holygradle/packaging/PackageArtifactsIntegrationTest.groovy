package holygradle.packaging

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

class PackageArtifactsIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testBasicConfiguration() {
        File projectDir = new File(getTestDir(), "projectA")

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.ext.holyGradleInitScriptVersion = "1.2.3.4" // Required by custom-gradle-core-plugin.
        project.ext.holyGradlePluginsRepository = ""
        project.apply plugin: 'intrepid'

        Collection<PackageArtifactHandler> packageArtifacts =
            project.extensions.findByName("packageArtifacts") as Collection<PackageArtifactHandler>
        assertNotNull(packageArtifacts)
    }
    
    @Test
    public void testBasicPackageEverything() {
        File projectDir = new File(getTestDir(), "projectB")
        File packagesDir = new File(projectDir, "packages")
        if (packagesDir.exists()) {
            packagesDir.deleteDir()
        }
        
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("packageEverything")
        }
        
        assertTrue(packagesDir.exists())
        assertTrue(new File(packagesDir, "projectB-foo.zip").exists())
        assertTrue(new File(packagesDir, "projectB-bar.zip").exists())
        assertTrue(new File(packagesDir, "projectB-buildScript.zip").exists())
    }
}

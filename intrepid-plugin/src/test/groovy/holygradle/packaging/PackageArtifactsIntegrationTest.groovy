package holygradle.packaging

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import java.util.zip.ZipFile

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

        // Run fAD to make sure we generate the settings file.
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")
        }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("packageEverything")
        }
        
        assertTrue(packagesDir.exists())
        assertTrue(new File(packagesDir, "projectB-foo.zip").exists())
        assertTrue(new File(packagesDir, "projectB-bar.zip").exists())
        File buildScriptFile = new File(packagesDir, "projectB-buildScript.zip")
        assertTrue(buildScriptFile.exists())
        ZipFile buildScriptZipFile = new ZipFile(buildScriptFile)
        assertNotNull("build file is in zip", buildScriptZipFile.getEntry("build.gradle"))
        assertNotNull("settings file is in zip", buildScriptZipFile.getEntry("settings.gradle"))
    }
}

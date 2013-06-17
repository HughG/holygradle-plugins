package holygradle.packaging

import holygradle.test.TestBase
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

class PackageArtifactsTest extends TestBase {
    @Test
    public void testBasicConfiguration() {
        def projectDir = new File(getTestDir(), "projectA")
        
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.ext.holyGradleInitScriptVersion = "1.2.3.4" // Required by custom-gradle-core-plugin.
        project.ext.holyGradlePluginsRepository = ""
        project.apply plugin: 'intrepid'
        
        def packageArtifacts = project.extensions.findByName("packageArtifacts")
        assertNotNull(packageArtifacts)
        
        //assertEquals(2, packageArtifacts.size())
    }
    
    @Test
    public void testBasicPackageEverything() {
        def projectDir = new File(getTestDir(), "projectB")
        def packagesDir = new File(projectDir, "packages")
        if (packagesDir.exists()) {
            packagesDir.deleteDir()
        }
        
        invokeGradle(projectDir) {
            forTasks("packageEverything")
        }
        
        assertTrue(packagesDir.exists())
        assertTrue(new File(packagesDir, "projectB-foo.zip").exists())
        assertTrue(new File(packagesDir, "projectB-bar.zip").exists())
        assertTrue(new File(packagesDir, "projectB-buildScript.zip").exists())
    }
}

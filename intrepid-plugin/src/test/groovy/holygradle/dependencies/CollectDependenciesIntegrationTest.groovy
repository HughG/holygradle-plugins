package holygradle.dependencies

import holygradle.packaging.PackageArtifactsIntegrationTest
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import java.nio.file.Files

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class CollectDependenciesIntegrationTest extends AbstractHolyGradleIntegrationTest {
    /**
     * Test that, if you depend on a module only via configurations which have no artifacts, and those lead to other
     * module configurations which do have artifacts, then all those modules are correctly collected.  The one with
     * empty configurations will just have an ivy.xml file.
     *
     * This is a (regression) test for #4088.
     */
    @Test
    public void useOnlyViaEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useOnlyViaEmptyConfigs")
        final File emptyConfigLibDir = new File(projectDir, "empty-config-lib")
        final Collection<String> allCollectedDepDirNames = [
            "empty-config-lib",
            "external-lib",
            "another-lib"
        ]

        doCollectDependenciesTest(projectDir, allCollectedDepDirNames, emptyConfigLibDir)
    }

    /**
     * Test that, if you depend on a module only via configurations which have no artifacts, then that module is
     * correctly collected, even if no other modules are involved.  It will just have an ivy.xml file.
     *
     * This is a (regression) test for #4088.
     */
    @Test
    public void useOnlyEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useOnlyEmptyConfigs")
        final File emptyConfigLibDir = new File(projectDir, "empty-config-lib")
        final Collection<String> allCollectedDepDirNames = ["empty-config-lib"]

        doCollectDependenciesTest(projectDir, allCollectedDepDirNames, emptyConfigLibDir)
    }

    private void doCollectDependenciesTest(
        File projectDir,
        Collection<String> allCollectedDepDirNames,
        File emptyConfigLibDir
    ) {
        final File collectedDependenciesDir = new File(projectDir, "local_artifacts")
        if (collectedDependenciesDir.exists()) {
            assertTrue("Removed existing ${collectedDependenciesDir}", collectedDependenciesDir.deleteDir())
        }
        final File gradleDir = new File(projectDir, "gradle")
        if (gradleDir.exists()) {
            assertTrue("Removed existing ${gradleDir}", gradleDir.deleteDir())
        }
        assertTrue("Created empty ${gradleDir}", gradleDir.mkdir())
        Files.copy(
            new File(projectDir, "../dummy-gradle-wrapper.properties").toPath(),
            new File(gradleDir, "gradle-wrapper.properties").toPath()
        )

        // Create a dummy project to provide access to FileTree methods
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks("collectDependencies")
        }

        assertTrue("Collected dependencies folder exists", collectedDependenciesDir.exists())
        final File groupDir = new File(collectedDependenciesDir, "ivy/holygradle.test")
        final Collection<File> allCollectedDepDirs = allCollectedDepDirNames.collect { new File(groupDir, it) }
        allCollectedDepDirs.each { File dir ->
            assertTrue("Collected dep folder exists at ${dir}", dir.exists())
            final File depVersionDir = new File(dir, "1.0")
            assertTrue("Collected dep version folder exists at ${depVersionDir}", depVersionDir.exists())
            final File ivyFile = new File(depVersionDir, "ivy-1.0.xml")
            assertTrue("Collected dep ivy file exists at ${ivyFile}", ivyFile.exists())
            // Here we check for >1 file to decide if there are artifacts, because we always expect one file, ivy.xml.
            boolean expectArtifacts = (dir.name != emptyConfigLibDir.name)
            assertEquals(
                "Expecting ${expectArtifacts ? 'some' : 'no'} artifacts",
                expectArtifacts,
                depVersionDir.list().length > 1
            )
        }

        PackageArtifactsIntegrationTest.checkBuildInfo(project.fileTree(new File("local_artifacts")))
    }
}

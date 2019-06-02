package holygradle.scm

import holygradle.io.FileHelper
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class SourceControlRepositoriesTest extends AbstractHolyGradleTest {
    @Test
    public void testDummy() {
        File dummyDir = new File(getTestDir(), "dummy")
        Project project = prepareRepoDir(dummyDir)

        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertNotNull(sourceControl)
        assertEquals("${project} sourceControl is of type ${DummySourceControl}", DummySourceControl, sourceControl.getClass())
        assertEquals(null, sourceControl.getRevision())
        assertFalse(sourceControl.hasLocalChanges)
        assertEquals("dummy:url", sourceControl.getUrl())
        assertEquals("n/a", sourceControl.getProtocol())
        assertEquals(new File("__dummy__file__"), sourceControl.getLocalDir())
    }

    @Test
    public void testGetWithoutDummy() {
        File dummyDir = new File(getTestDir(), "dummy")
        Project project = prepareRepoDir(dummyDir)

        Collection<SourceDependencyHandler> sourceDependencies = SourceDependencyHandler.createContainer(project)

        // Make a dummy source dependency pointing to an already-existing folder.
        final SourceDependencyHandler handler = sourceDependencies.create("dummySourceDep")
        FileHelper.ensureMkdirs(handler.destinationDir)

        SourceControlRepository repo = SourceControlRepositories.create(handler)
        assertTrue("Expect no repo for an empty source dependency directory; got ${repo}", repo instanceof DummySourceControl)
    }

    private static Project prepareRepoDir(File repoDir) {
        FileHelper.ensureDeleteDirRecursive(repoDir)
        FileHelper.ensureMkdirs(repoDir)
        Project project = ProjectBuilder.builder().withProjectDir(repoDir).build()
        new File(repoDir, "build.gradle").text = "/* Dummy Gradle build file. */"
        SourceControlRepositories.createExtension(project)
        return project
    }
}
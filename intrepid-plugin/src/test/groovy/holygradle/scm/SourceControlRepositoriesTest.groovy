package holygradle.scm

import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class SourceControlRepositoriesTest extends AbstractHolyGradleTest {
   @Test
    public void testDummy() {
        File dummyDir = new File(getTestDir(), "dummy")

        Project project = ProjectBuilder.builder().withProjectDir(dummyDir).build()
        SourceControlRepositories.createExtension(project)
        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertNotNull(sourceControl)
        assertEquals("${project} sourceControl is of type ${DummySourceControl}", DummySourceControl, sourceControl.getClass())
        assertEquals(null, sourceControl.getRevision())
        assertFalse(sourceControl.hasLocalChanges())
        assertEquals(null, sourceControl.getUrl())
        assertEquals("n/a", sourceControl.getProtocol())
        assertEquals(null, sourceControl.getLocalDir())
    }

    @Test
    public void testGetWithoutDummy() {
        File dummyDir = new File(getTestDir(), "dummy")
        Project project = ProjectBuilder.builder().withProjectDir(dummyDir).build()
        SourceControlRepository repo = SourceControlRepositories.create(project, dummyDir)
        assertNull(repo)
    }
}
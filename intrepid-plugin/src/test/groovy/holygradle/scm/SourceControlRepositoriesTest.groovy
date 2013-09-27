package holygradle.scm

import holygradle.test.*
import holygradle.testUtil.ExecUtil
import holygradle.testUtil.ZipUtil
import org.gradle.api.Project
import org.gradle.process.ExecSpec
import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

class SourceControlRepositoriesTest extends AbstractHolyGradleTest {
    @Test
    public void testSvn() {
        File svnDir = ZipUtil.extractZip(getTestDir(), "test_svn")
        
        Project project = ProjectBuilder.builder().withProjectDir(svnDir).build()
        SourceControlRepositories.createExtension(project)
        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository
        
        assertTrue(sourceControl instanceof SvnRepository)
        assertEquals("1", sourceControl.getRevision())
        assertFalse(sourceControl.hasLocalChanges())
        assertEquals("file:///C:/Projects/DependencyManagement/Project/test_svn_repo/trunk", sourceControl.getUrl())
        assertEquals("svn", sourceControl.getProtocol())
        assertEquals(svnDir.getCanonicalFile(), sourceControl.getLocalDir())
        
        // hasLocalChanges isn't implemented properly yet for SVN. After changing a file it still 
        // reports no local changes.
        File helloFile = new File(svnDir, "hello.txt")
        helloFile.write("bonjour")
        // assertTrue(sourceControl.hasLocalChanges())
    }

    @Test
    public void testDummy() {
        File dummyDir = new File(getTestDir(), "dummy")

        Project project = ProjectBuilder.builder().withProjectDir(dummyDir).build()
        SourceControlRepositories.createExtension(project)
        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertNotNull(sourceControl)
        assertTrue(sourceControl instanceof DummySourceControl)
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
        SourceControlRepository repo = SourceControlRepositories.get(project, dummyDir)
        assertNull(repo)
    }
}
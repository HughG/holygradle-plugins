package holygradle.scm

import holygradle.test.*
import holygradle.testUtil.ZipUtil
import org.gradle.api.Project
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
    public void canGetMercurialRevision() {
        def expectedRevision = "12345abcdefg"
        def hgCommand = [ execute : { args -> "12345abcdefg"} ] as HgCommand
        def repo = new HgRepository(hgCommand, new File(getTestDir(), "test_hg"))
        def actualRevision = repo.getRevision()

        assertEquals(expectedRevision, actualRevision)
    }

    @Test
    public void canDetermineThatThereAreLocalChanges() {
        def changedFileList = ["M some_modified_file.txt","A some_added_file.foo"]
        def hgCommand = [ execute : {args -> changedFileList.join("\n")} ] as HgCommand
        def repo = new HgRepository(hgCommand, new File(getTestDir(), "test_hg"))

        assertTrue(repo.hasLocalChanges())
    }

    @Test
    public void canDetermineThatThereAreNoLocalChanges() {
        def changedFileList = "\n"
        def hgCommand = [ execute: {args -> changedFileList} ] as HgCommand
        def repo = new HgRepository(hgCommand, new File(getTestDir(), "test_hg"))

        assertFalse(repo.hasLocalChanges())
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
        Project dummyProject = [ getProjectDir : { dummyDir }] as Project
        SourceControlRepository repo = SourceControlRepositories.get(dummyProject)
        assertNull(repo)
    }
}
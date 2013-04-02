package holygradle.scm

import holygradle.*
import holygradle.test.*
import org.gradle.api.Project
import org.junit.Test
import net.lingala.zip4j.core.ZipFile
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

class SourceControlRepositoriesTest extends TestBase {
    private File extractZip(String zipName) {
        def zipDir = new File(getTestDir(), zipName)
        if (zipDir.exists()) {
            zipDir.deleteDir()
        }
        ZipFile zipFile = new ZipFile(new File(getTestDir().parentFile, zipName + ".zip"))
        zipFile.extractAll(zipDir.path)
        zipDir
    }
    
    @Test
    public void testSvn() {
        def svnDir = extractZip("test_svn")
        
        Project project = ProjectBuilder.builder().withProjectDir(svnDir).build()
        SourceControlRepositories.createExtension(project)
        def sourceControl = project.extensions.findByName("sourceControl")
        
        assertTrue(sourceControl instanceof SvnRepository)
        assertEquals("1", sourceControl.getRevision())
        assertFalse(sourceControl.hasLocalChanges())
        assertEquals("file:///C:/Projects/DependencyManagement/Project/test_svn_repo/trunk", sourceControl.getUrl())
        assertEquals("svn", sourceControl.getProtocol())
        assertEquals(svnDir.getCanonicalFile(), sourceControl.getLocalDir())
        
        // hasLocalChanges isn't implemented properly yet for SVN. After changing a file it still 
        // reports no local changes.
        def helloFile = new File(svnDir, "hello.txt")
        helloFile.write("bonjour")
        assertFalse(sourceControl.hasLocalChanges())
    }
    
    @Test
    public void testHg() {
        def hgDir = extractZip("test_hg")
        
        Project project = ProjectBuilder.builder().withProjectDir(hgDir).build()
        SourceControlRepositories.createExtension(project)
        def sourceControl = project.extensions.findByName("sourceControl")
        
        assertTrue(sourceControl instanceof HgRepository)
        assertEquals("9cc27b0a5c28746662dc66e50b2d2d0d25897b90", sourceControl.getRevision())
        assertFalse(sourceControl.hasLocalChanges())
        assertEquals("unknown", sourceControl.getUrl())
        assertEquals("hg", sourceControl.getProtocol())
        assertEquals(hgDir.getCanonicalFile(), sourceControl.getLocalDir())
        
        def ahoyFile = new File(hgDir, "ahoy.txt")
        ahoyFile.write("bof")
        assertTrue(sourceControl.hasLocalChanges())
    }
    
    @Test
    public void testDummy() {
        def dummyDir = new File(getTestDir(), "dummy")
        
        Project project = ProjectBuilder.builder().withProjectDir(dummyDir).build()
        SourceControlRepositories.createExtension(project)
        def sourceControl = project.extensions.findByName("sourceControl")
        
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
        def dummyDir = new File(getTestDir(), "dummy")
        
        def repo = SourceControlRepositories.get(dummyDir)
        assertNull(repo)
    }
}
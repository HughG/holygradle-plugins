package holygradle.artifactory_manager

import groovy.json.JsonSlurper
import org.junit.Test

import static org.mockito.Mockito.*

/*def project = ProjectBuilder.builder().build()
project.apply plugin: "artifactory-manager"
*/
        
class DeletionTest {
    private static Map folderInfo(String date, def children) {
        def dateStr = Date.parse("yyyy-MM-dd", date).format("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        println dateStr
        def childrenStr = children.collect { "{\"uri\":\"${it}\",\"folder\":true}" }.join(",")
        new JsonSlurper().parseText("{\"created\": \"${dateStr}\", \"children\": [${childrenStr}]}") as Map
    }
    
    private static ArtifactoryAPI getMockArtifactory(String date) {
        ArtifactoryAPI artifactory = mock(ArtifactoryAPI.class)
        when(artifactory.getNow()).thenReturn(Date.parse("yyyy-MM-dd", date))
        artifactory
    }
    
    @Test
    public void testDeleteOlderThanOneWeek() {
        def artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1", "/1.2"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-12-19", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-12-17", []))
        
        def artifactoryManager = new ArtifactoryManagerHandler(artifactory)
        
        artifactoryManager.delete("org:foo") {
            olderThan(1, "week")
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.1")
        verify(artifactory, never()).removeItem("org/foo")
    }
    
    @Test
    public void testDeleteOlderThanOneMonth() {
        def artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1", "/1.2", "/1.3", "/1.4"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-11-24", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-11-26", []))
        when(artifactory.getFolderInfoJson("org/foo/1.3")).thenReturn(folderInfo("2012-11-01", []))
        when(artifactory.getFolderInfoJson("org/foo/1.4")).thenReturn(folderInfo("2012-12-01", []))
        
        def artifactoryManager = new ArtifactoryManagerHandler(artifactory)
        
        artifactoryManager.delete("org:foo") {
            olderThan(1, "month")
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory).removeItem("org/foo/1.1")
        verify(artifactory).removeItem("org/foo/1.3")
        verify(artifactory, never()).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.4")
        verify(artifactory, never()).removeItem("org/foo")
    }
    
     @Test
    public void testDeleteKeepingSpecificVersion() {
        def artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1", "/1.2", "/1.3", "/1.4"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-11-24", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-11-26", []))
        when(artifactory.getFolderInfoJson("org/foo/1.3")).thenReturn(folderInfo("2012-11-01", []))
        when(artifactory.getFolderInfoJson("org/foo/1.4")).thenReturn(folderInfo("2012-12-01", []))
        
        def artifactoryManager = new ArtifactoryManagerHandler(artifactory)
        
        artifactoryManager.delete("org:foo") {
            olderThan(1, "month")
            dontDelete("1.3")
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory).removeItem("org/foo/1.1")
        verify(artifactory, never()).removeItem("org/foo/1.3")
        verify(artifactory, never()).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.4")
        verify(artifactory, never()).removeItem("org/foo")
    }
    
    @Test
    public void testDeleteWhenOnlyOneItemLeft() {
        def artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-11-01", []))
        
        def artifactoryManager = new ArtifactoryManagerHandler(artifactory)
        
        artifactoryManager.delete("org:foo") {
            olderThan(1, "week")
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory, never()).removeItem("org/foo")
        verify(artifactory, never()).removeItem("org/foo/1.1")
        verify(artifactory, never()).removeItem("org/foo/1.2")
    }
    
    @Test
    public void testDeleteKeepingOneItemPerWeek() {
        def artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("")).thenReturn(folderInfo("2012-09-19", ["org"]))
        when(artifactory.getFolderInfoJson("org")).thenReturn(folderInfo("2012-09-19", ["/foo"]))
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-12-01", ["/1.1", "/1.2", "/1.3", "/1.4"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-09-19", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-09-20", []))
        when(artifactory.getFolderInfoJson("org/foo/1.3")).thenReturn(folderInfo("2012-10-09", []))
        when(artifactory.getFolderInfoJson("org/foo/1.4")).thenReturn(folderInfo("2012-10-22", []))

        def artifactoryManager = new ArtifactoryManagerHandler(artifactory)
        
        artifactoryManager.delete {
            olderThan(1, "month")
            keepOneBuildPer(1, "week")
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory, never()).removeItem("org/foo")
        verify(artifactory, never()).removeItem("org/foo/1.1")
        verify(artifactory, times(1)).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.3")
        verify(artifactory, never()).removeItem("org/foo/1.4")
    }
    
    @Test
    public void testDeleteMultiRepoKeepingOneItemPerWeek() {
        def artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-12-01", ["/1.1", "/1.2", "/1.3", "/1.4", "/1.5", "/1.6", "/1.7"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-09-19", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-09-20", []))
        when(artifactory.getFolderInfoJson("org/foo/1.3")).thenReturn(folderInfo("2012-10-09", []))
        when(artifactory.getFolderInfoJson("org/foo/1.4")).thenReturn(folderInfo("2012-10-22", []))
        when(artifactory.getFolderInfoJson("org/foo/1.5")).thenReturn(folderInfo("2012-10-22", []))
        when(artifactory.getFolderInfoJson("org/foo/1.6")).thenReturn(folderInfo("2012-10-24", []))
        when(artifactory.getFolderInfoJson("org/foo/1.7")).thenReturn(folderInfo("2012-12-02", []))
        
        when(artifactory.getFolderInfoJson("org/bar")).thenReturn(folderInfo("2012-12-01", ["/1.1", "/1.2", "/1.3", "/1.4", "/1.5", "/1.6", "/1.7"]))
        when(artifactory.getFolderInfoJson("org/bar/1.1")).thenReturn(folderInfo("2012-09-19", []))
        when(artifactory.getFolderInfoJson("org/bar/1.2")).thenReturn(folderInfo("2012-09-20", []))
        when(artifactory.getFolderInfoJson("org/bar/1.3")).thenReturn(folderInfo("2012-10-09", []))
        when(artifactory.getFolderInfoJson("org/bar/1.4")).thenReturn(folderInfo("2012-10-22", []))
        when(artifactory.getFolderInfoJson("org/bar/1.5")).thenReturn(folderInfo("2012-10-22", []))
        when(artifactory.getFolderInfoJson("org/bar/1.6")).thenReturn(folderInfo("2012-10-24", []))
        when(artifactory.getFolderInfoJson("org/bar/1.7")).thenReturn(folderInfo("2012-12-02", []))

        def artifactoryManager = new ArtifactoryManagerHandler(artifactory)
        
        artifactoryManager.delete("org:foo,org:bar") {
            olderThan(1, "month")
            keepOneBuildPer(1, "week")
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory, never()).removeItem("org/foo")
        verify(artifactory, never()).removeItem("org/foo/1.1")
        verify(artifactory, times(1)).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.3")
        verify(artifactory, never()).removeItem("org/foo/1.4")
        verify(artifactory, times(1)).removeItem("org/foo/1.5")
        verify(artifactory, times(1)).removeItem("org/foo/1.6")
        verify(artifactory, never()).removeItem("org/foo/1.7")
        
        verify(artifactory, never()).removeItem("org/bar")
        verify(artifactory, never()).removeItem("org/bar/1.1")
        verify(artifactory, times(1)).removeItem("org/bar/1.2")
        verify(artifactory, never()).removeItem("org/bar/1.3")
        verify(artifactory, never()).removeItem("org/bar/1.4")
        verify(artifactory, times(1)).removeItem("org/bar/1.5")
        verify(artifactory, times(1)).removeItem("org/bar/1.6")
        verify(artifactory, never()).removeItem("org/bar/1.7")
    }
}
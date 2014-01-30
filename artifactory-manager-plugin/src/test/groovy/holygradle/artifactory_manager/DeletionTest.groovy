package holygradle.artifactory_manager

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.mockito.Mockito.*

class DeletionTest {
    private static Map folderInfoInternal(String dateStr, Collection<String> children) {
        String childrenStr = children.collect { "{\"uri\":\"${it}\",\"folder\":true}" }.join(",")
        new JsonSlurper().parseText("{\"created\": \"${dateStr}\", \"children\": [${childrenStr}]}") as Map
    }

    private static Map folderInfo(String date, Collection<String> children) {
        String dateStr = Date.parse("yyyy-MM-dd", date).format("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        println dateStr
        return folderInfoInternal(dateStr, children)
    }

    private static Map folderInfoWithTime(String date, Collection<String> children) {
        String dateStr = Date.parse("yyyy-MM-dd HH:mm:ss", date).format("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        println dateStr
        return folderInfoInternal(dateStr, children)
    }

    private static ArtifactoryAPI getMockArtifactory(String date) {
        ArtifactoryAPI artifactory = mock(ArtifactoryAPI.class)
        when(artifactory.getNow()).thenReturn(Date.parse("yyyy-MM-dd", date))
        artifactory
    }

    @Test(expected = IllegalStateException.class)
    public void testDeleteNoModulesFails() {
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1", "/1.2"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-12-19", []))

        Project project = ProjectBuilder.builder().withName("test").build()
        ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)

        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("") { DeleteRequest it ->
                it.olderThan(1, "week")
            }
        }

        artifactoryManager.doDelete(true)
    }

    @Test
    public void testDeleteOlderThanOneWeek() {
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1", "/1.2"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-12-19", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-12-17", []))

        Project project = ProjectBuilder.builder().withName("test").build()
        ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)
        
        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("org:foo") { DeleteRequest it ->
                it.olderThan(1, "week")
            }
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.1")
        verify(artifactory, never()).removeItem("org/foo")
    }
    
    @Test
    public void testDeleteOlderThanOneMonth() {
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1", "/1.2", "/1.3", "/1.4"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-11-24", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-11-26", []))
        when(artifactory.getFolderInfoJson("org/foo/1.3")).thenReturn(folderInfo("2012-11-01", []))
        when(artifactory.getFolderInfoJson("org/foo/1.4")).thenReturn(folderInfo("2012-12-01", []))

        Project project = ProjectBuilder.builder().withName("test").build()
        ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)
        
        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("org:foo") { DeleteRequest it ->
                it.olderThan(1, "month")
            }
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
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1", "/1.2", "/1.3", "/1.4"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-11-24", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-11-26", []))
        when(artifactory.getFolderInfoJson("org/foo/1.3")).thenReturn(folderInfo("2012-11-01", []))
        when(artifactory.getFolderInfoJson("org/foo/1.4")).thenReturn(folderInfo("2012-12-01", []))

         Project project = ProjectBuilder.builder().withName("test").build()
         ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)
        
        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("org:foo") { DeleteRequest it ->
                it.olderThan(1, "month")
                it.dontDelete("1.3")
            }
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory).removeItem("org/foo/1.1")
        verify(artifactory, never()).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.3")
        verify(artifactory, never()).removeItem("org/foo/1.4")
        verify(artifactory, never()).removeItem("org/foo")
    }
    
    @Test
    public void testDeleteWhenOnlyOneItemLeft() {
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-11-01", ["/1.1"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-11-01", []))

        Project project = ProjectBuilder.builder().withName("test").build()
        ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)
        
        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("org:foo") { DeleteRequest it ->
                it.olderThan(1, "week")
            }
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory, never()).removeItem("org/foo")
        verify(artifactory, never()).removeItem("org/foo/1.1")
    }
    
    @Test
    public void testDeleteKeepingOneItemPerWeek() {
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("")).thenReturn(folderInfo("2012-09-19", ["org"]))
        when(artifactory.getFolderInfoJson("org")).thenReturn(folderInfo("2012-09-19", ["/foo"]))
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(folderInfo("2012-12-01", ["/1.1", "/1.2", "/1.3", "/1.4"]))
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-09-19", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfo("2012-09-20", []))
        when(artifactory.getFolderInfoJson("org/foo/1.3")).thenReturn(folderInfo("2012-10-09", []))
        when(artifactory.getFolderInfoJson("org/foo/1.4")).thenReturn(folderInfo("2012-10-22", []))

        Project project = ProjectBuilder.builder().withName("test").build()
        ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)
        
        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("org:foo") { DeleteRequest it ->
                it.olderThan(1, "month")
                it.keepOneBuildPer(1, "week")
            }
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory, never()).removeItem("org/foo")
        verify(artifactory, times(1)).removeItem("org/foo/1.1")
        verify(artifactory, never()).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.3")
        verify(artifactory, never()).removeItem("org/foo/1.4")
    }


    @Test
    public void testDeleteKeepingOneItemPerDay() {
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("")).thenReturn(folderInfo("2012-09-19", ["org"]))
        when(artifactory.getFolderInfoJson("org")).thenReturn(folderInfo("2012-09-19", ["/foo"]))
        when(artifactory.getFolderInfoJson("org/foo")).thenReturn(
            folderInfo("2012-12-01", ["/1.1", "/1.2", "/1.3", "/1.4", "/1.5", "/1.6", "/1.7", "/1.8"])
        )
        // NOTE 2014-01-28 hgreene: The two entries here (apart from the newest, which is never deleted) with no time
        // test the edge case of an item having a Date which is exactly at midnight.  Because of that, they will be
        // exactly equal to the start Date for the sliding window.  It should be included in consideration for deletion.
        when(artifactory.getFolderInfoJson("org/foo/1.1")).thenReturn(folderInfo("2012-09-19", []))
        when(artifactory.getFolderInfoJson("org/foo/1.2")).thenReturn(folderInfoWithTime("2012-09-20 10:34:30", []))
        when(artifactory.getFolderInfoJson("org/foo/1.3")).thenReturn(folderInfoWithTime("2012-09-20 12:23:11", []))
        when(artifactory.getFolderInfoJson("org/foo/1.4")).thenReturn(folderInfoWithTime("2012-10-09 09:46:14", []))
        when(artifactory.getFolderInfoJson("org/foo/1.5")).thenReturn(folderInfoWithTime("2012-10-09 13:03:45", []))
        when(artifactory.getFolderInfoJson("org/foo/1.6")).thenReturn(folderInfoWithTime("2012-10-09 14:43:56", []))
        when(artifactory.getFolderInfoJson("org/foo/1.7")).thenReturn(folderInfo("2012-10-15", []))
        when(artifactory.getFolderInfoJson("org/foo/1.8")).thenReturn(folderInfo("2012-10-22", []))

        Project project = ProjectBuilder.builder().withName("test").build()
        ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)

        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("org:foo") { DeleteRequest it ->
                it.olderThan(1, "month")
                it.keepOneBuildPer(1, "day")
            }
        }

        artifactoryManager.doDelete(true)

        // Note: The expectations here also test that, when there are several versions within an interval, the most
        // recent version is kept (rather than the oldest, as previously was the case).
        verify(artifactory, never()).removeItem("org/foo")
        verify(artifactory, never()).removeItem("org/foo/1.1")
        verify(artifactory, times(1)).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.3")
        verify(artifactory, times(1)).removeItem("org/foo/1.4")
        verify(artifactory, times(1)).removeItem("org/foo/1.5")
        verify(artifactory, never()).removeItem("org/foo/1.6")
        verify(artifactory, never()).removeItem("org/foo/1.7")
        verify(artifactory, never()).removeItem("org/foo/1.8")
    }

    @Test
    public void testDeleteMultiModuleKeepingOneItemPerWeek() {
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
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

        Project project = ProjectBuilder.builder().withName("test").build()
        ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)
        
        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("org:foo,org:bar") { DeleteRequest it ->
                it.olderThan(1, "month")
                it.keepOneBuildPer(1, "week")
            }
        }
        
        artifactoryManager.doDelete(true)
                
        verify(artifactory, never()).removeItem("org/foo")
        verify(artifactory, times(1)).removeItem("org/foo/1.1")
        verify(artifactory, never()).removeItem("org/foo/1.2")
        verify(artifactory, never()).removeItem("org/foo/1.3")
        verify(artifactory, times(1)).removeItem("org/foo/1.4")
        verify(artifactory, times(1)).removeItem("org/foo/1.5")
        verify(artifactory, never()).removeItem("org/foo/1.6")
        verify(artifactory, never()).removeItem("org/foo/1.7")
        
        verify(artifactory, never()).removeItem("org/bar")
        verify(artifactory, times(1)).removeItem("org/bar/1.1")
        verify(artifactory, never()).removeItem("org/bar/1.2")
        verify(artifactory, never()).removeItem("org/bar/1.3")
        verify(artifactory, times(1)).removeItem("org/bar/1.4")
        verify(artifactory, times(1)).removeItem("org/bar/1.5")
        verify(artifactory, never()).removeItem("org/bar/1.6")
        verify(artifactory, never()).removeItem("org/bar/1.7")
    }

    @Test
    public void testDeleteVersionsMatchingRegex() {
        ArtifactoryAPI artifactory = getMockArtifactory("2012-12-25")
        when(artifactory.getFolderInfoJson("org/foo"))
            .thenReturn(folderInfo("2012-11-01", ["/trunk_1", "/trunk_manual_hack", "/trunk_2", "/rel_1", "/rel_2"]))
        // Old enough to be removed, and matches regex.
        when(artifactory.getFolderInfoJson("org/foo/trunk_1")).thenReturn(folderInfo("2012-11-20", []))
        // Old enough to be removed, but doesn't match regex.
        when(artifactory.getFolderInfoJson("org/foo/trunk_manual_hack")).thenReturn(folderInfo("2012-11-21", []))
        // Matches regex, but not old enough to be removed.
        when(artifactory.getFolderInfoJson("org/foo/trunk_2")).thenReturn(folderInfo("2012-11-26", []))
        // Old enough to be removed, but doesn't match regex.
        when(artifactory.getFolderInfoJson("org/foo/rel_1")).thenReturn(folderInfo("2012-11-01", []))
        // Not old enough to be removed, and doesn't match regex.
        when(artifactory.getFolderInfoJson("org/foo/rel_2")).thenReturn(folderInfo("2012-12-01", []))

        Project project = ProjectBuilder.builder().withName("test").build()
        ArtifactoryManagerHandler artifactoryManager = new ArtifactoryManagerHandler(project, artifactory)

        artifactoryManager.repository("none") { RepositoryHandler repo ->
            repo.delete("org:foo") { DeleteRequest it ->
                it.versionsMatching("trunk_[0-9]+")
                it.olderThan(1, "month")
            }
        }

        artifactoryManager.doDelete(true)

        verify(artifactory).removeItem("org/foo/trunk_1")
        verify(artifactory, never()).removeItem("org/foo/trunk_manual_hack")
        verify(artifactory, never()).removeItem("org/foo/trunk_2")
        verify(artifactory, never()).removeItem("org/foo/rel_1")
        verify(artifactory, never()).removeItem("org/rel_2")
    }

}
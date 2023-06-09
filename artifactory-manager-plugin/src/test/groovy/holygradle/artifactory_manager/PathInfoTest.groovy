package holygradle.artifactory_manager

import org.junit.Test

import static org.junit.Assert.*
import static org.mockito.Mockito.*
import groovy.json.JsonSlurper

class PathInfoTest {
    public static FolderInfo folderInfo(String date) {
        new FolderInfo(new JsonSlurper().parseText("{\"created\": \"${date}\"}") as Map)
    }

    private static ArtifactoryAPI getMockArtifactory() {
        mock(ArtifactoryAPI.class)
    }

    @Test
    public void testDateParsingHandlesTimeZonesCorrectly() {
        ArtifactoryAPI artifactory = getMockArtifactory()

        // Construct dates to be parsed, and mock objects that will pass them to the PathInfo objects under test.
        final String winterDateString = "2013-02-26T15:45:23.516Z"
        final String summerDateString = "2013-06-01T15:32:02.611+01:00"
        when(artifactory.getFolderInfo("org/foo/winter")).thenReturn(folderInfo(winterDateString))
        when(artifactory.getFolderInfo("org/foo/summer")).thenReturn(folderInfo(summerDateString))

        // Construct the objects under test.
        PathInfo winterArtifactInfo = new PathInfo(artifactory, "org/foo/winter")
        PathInfo summerArtifactInfo = new PathInfo(artifactory, "org/foo/summer")

        // Construct dates matching the strings above.  Note that months are zero-based in Java calendars.
        Calendar winterCalendar = new GregorianCalendar()
        winterCalendar.setTimeZone(TimeZone.getTimeZone("Europe/London"))
        winterCalendar.set(2013, 1, 26, 15, 45, 23)
        winterCalendar.set(Calendar.MILLISECOND, 516)

        Calendar summerCalendar = new GregorianCalendar()
        summerCalendar.setTimeZone(TimeZone.getTimeZone("Europe/London"))
        summerCalendar.set(2013, 5, 01, 15, 32, 02)
        summerCalendar.set(Calendar.MILLISECOND, 611)

        // Test the parsing.
        assertEquals(winterCalendar.getTime(), winterArtifactInfo.getCreationDate())
        assertEquals(summerCalendar.getTime(), summerArtifactInfo.getCreationDate())

    }
}

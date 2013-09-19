package holygradle.scm

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.testUtil.ZipUtil
import org.junit.Test

import java.nio.file.Files

class SourceControlRepositoriesIntegrationTest extends AbstractHolyGradleIntegrationTest {
    /**
     * Integration test of the HgRepository class.  Most of this test is actually in the "build.gradle" script which is
     * launched.  This is because the test needs to run the version of Mercurial which the intrepid depends on, and the
     * only easy way to do that is to run a build which uses the intrepid plugin.
     */
    @Test
    public void testHg() {
        // Re-create and re-set-up the test project dir.
        File hgDir = ZipUtil.extractZip(getTestDir(), "test_hg")
        ["build.gradle"].each { String file ->
            Files.copy(
                new File(getTestDir(), file).toPath(),
                new File(hgDir, file).toPath()
            )
        }

        // Now run the test script.
        invokeGradle(hgDir) { }
    }
}
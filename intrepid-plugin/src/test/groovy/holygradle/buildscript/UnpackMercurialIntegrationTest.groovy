package holygradle.buildscript

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test
import static org.junit.Assert.*

/**
 * Integration test to check that intrepid correctly modifies the {@code mercurial.ini} file when it unpacks Mercurial.
 * This won't catch every possible real-world failure case, since the tweak only happens when extracting for the first
 * time, but at least it means extracting from clean will work.
 *
 * This test relies on the "cleanIntegrationTestState" in the root project deleting the unpacked Mercurial folder
 * each time before any integration tests are run.
 */
class UnpackMercurialIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testModifyIni() {
        File projectDir = new File(getTestDir(), "project")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("extractMercurial")
        }

        // Note: the following path might get out-of-date with the Mercurial version stated in the root build file for
        // the plugins.  In that case, you probably just want to update this string here to match.
        File hgIniFile = new File(gradleUserHome, "unpackCache/selenic/Mercurial-2.5.1/Mercurial.ini")
        assertTrue(
            "Mercurial.ini contains path to keyring extension",
            hgIniFile.readLines().any { String line ->
                line =~ /hgext\.mercurial_keyring = .+/
            }
        )
    }
}

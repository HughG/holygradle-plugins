package holygradle.symlinks

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

import static org.junit.Assert.*
import static org.hamcrest.number.OrderingComparison.*

class SymlinkTaskIntegrationTest extends AbstractHolyGradleIntegrationTest
{
    /**
     * Tests that, if one project A has a source dependency on another project B, then the symlinks to the unpack cache
     * for A will be rebuilt before any explicitly-created symlinks in B.  This is a requirement because project B might
     * want to symlink to an unpacked dependency in A, but it can't do that if A's symlinks-to-cache don't already
     * exist.
     *
     * We call the projects "A" and "B" here so that the tasks of "A" will be executed before those of "B" (since Gradle
     * does them in lexicographical order) unless there is some overriding task dependency (which is what we want to
     * test for).
     */
    @Test
    public void sourceDependencyCacheSymlinksAreCreatedFirst() {
        File projectDir = new File(getTestDir(), "vsSrcDeps")
        OutputStream outputStream = new ByteArrayOutputStream()
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("-m")
            launcher.forTasks("fAD")
            launcher.setStandardOutput(outputStream)
        }
        List<String> outputLines = outputStream.toString().readLines()

        // First, sanity-check that tasks of A in general are executed before those of B.  This check is here in case
        // someone renames the projects in future, which might make B's tasks come before A's anyway, which would obscure
        // whether or not a task dependency was forcing the order.
        int projAFADIndex = outputLines.findIndexOf { it.startsWith(":projA:fetchAllDependencies") }
        int projBFADIndex = outputLines.findIndexOf { it.startsWith(":projB:fetchAllDependencies") }
        assertNotEquals("Project A's top-level task is listed", -1, projAFADIndex)
        assertNotEquals("Project B's top-level task is listed", -1, projBFADIndex)
        assertThat(
            "Test internal condition: Project A's top-level task comes before project B's",
            projAFADIndex,
            lessThan(projBFADIndex)
        )

        // Now check the thing we really care about.
        int projBRebuildSymlinksToCacheIndex = outputLines.findIndexOf { it.startsWith(":projB:rebuildSymlinksToCache") }
        int projARebuildSymlinksIndex = outputLines.findIndexOf { it.startsWith(":projA:rebuildSymlinks") }
        assertNotEquals("Project B's symlinks-to-cache task is listed", -1, projBRebuildSymlinksToCacheIndex)
        assertNotEquals("Project B's symlinks task is listed", -1, projARebuildSymlinksIndex)
        assertThat(
            "Project B's symlinks-to-cache task comes before project A's symlinks task",
            projBRebuildSymlinksToCacheIndex,
            lessThan(projARebuildSymlinksIndex)
        )
    }
}

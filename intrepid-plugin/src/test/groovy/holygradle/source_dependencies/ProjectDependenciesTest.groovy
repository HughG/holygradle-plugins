package holygradle.source_dependencies

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Ignore
import org.junit.Test

import static org.junit.Assert.assertTrue

class ProjectDependenciesTest extends AbstractHolyGradleIntegrationTest {
    /**
     * Tests the situation where one project A (the root, or some sourceDependency) has a dependency on one version of a
     * packedDependency module, and another module/project B (in a sourceDependency) depends on a different version,
     * and there is a configuration of A which depends on a configuration of B.  Should fail with a conflict.
     */
    @Test
    public void conflict() {
        File projectDir = new File(getTestDir(), "conflict")
//        assertTrue(projectDir.exists()) // Need to include an assert or JUnit doesn't see this test!

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks")
            launcher.expectFailure(
                "Could not resolve all dependencies for configuration " + "':src_dep:everything'.",
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
        }
    }

    /**
     * Tests the situation where one project A (the root, or some sourceDependency) has a dependency on one version of a
     * packedDependency module, in configuration A1, and another module/project B (in a sourceDependency) depends on a
     * different version, in configuration B1, BUT there is no configuration of A that depends on B1.  Should succeed.
     */
    @Ignore("Will fail until GR #2291 is fixed")
    @Test
    public void conflictInUnusedConfig() {
        File projectDir = new File(getTestDir(), "conflictInUnusedConfig")
//        assertTrue(projectDir.exists()) // Need to include an assert or JUnit doesn't see this test!

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks")
        }
    }
}

package holygradle.source_dependencies

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.RegressionFileHelper
import holygradle.test.WrapperBuildLauncher
import org.junit.Ignore
import org.junit.Test

import static org.junit.Assert.assertTrue

class ProjectDependenciesIntegrationTest extends AbstractHolyGradleIntegrationTest {
    /**
     * Tests the situation where two projects have conflicting dependencies, but the task which is run doesn't need to
     * know about dependencies, so the conflict is not triggered.  This is mainly a test that simple tasks like "tasks"
     * don't trigger dependency resolution -- we want to avoid that, because it can be very slow.
     */
    @Test
    public void conflictNotTriggered() {
        File projectDir = new File(getTestDir(), "conflict")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("tasks")
        }
    }

    /**
     * Tests the situation where one project A (the root, or some sourceDependency) has a dependency on one version of a
     * packedDependency module, and another module/project B (in a sourceDependency) depends on a different version,
     * and there is a configuration of A which depends on a configuration of B.  Should fail with a conflict.
     */
    @Test
    public void conflict() {
        File projectDir = new File(getTestDir(), "conflict")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("extractPackedDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
"""> A conflict was found between the following modules:
   - holygradle.test:external-lib:1.0
   - holygradle.test:external-lib:1.1
"""
            ))
        }
    }

    /**
     * Tests the situation where one project A (the root, or some sourceDependency) has a dependency on one version of a
     * packedDependency module, in configuration A1, and another module/project B (in a sourceDependency) depends on a
     * different version, in configuration B1, BUT there is no configuration of A that depends on B1.  Should succeed.
     */
    @Test
    public void conflictInUnusedConfig() {
        File projectDir = new File(getTestDir(), "conflictInUnusedConfig")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("extractPackedDependencies")
        }
    }
}

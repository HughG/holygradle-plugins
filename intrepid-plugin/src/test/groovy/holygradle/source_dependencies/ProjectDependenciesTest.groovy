package holygradle.source_dependencies

import holygradle.*
import holygradle.test.*
import org.junit.Ignore
import org.junit.Test
import static org.junit.Assert.*

class ProjectDependenciesTest extends TestBase {

    /**
     * Tests the situation where one project A (the root, or some sourceDependency) has a dependency on one version of a
     * packedDependency module, and another module/project B (in a sourceDependency) depends on a different version,
     * and there is a configuration of A which depends on a configuration of B.  Should fail with a conflict.
     */
    @Test
    public void conflict() {
        def projectDir = new File(getTestDir(), "conflict")

        invokeGradle(projectDir) {
            forTasks("tasks")
            expectFailure(
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
        def projectDir = new File(getTestDir(), "conflictInUnusedConfig")

        invokeGradle(projectDir) {
            forTasks("tasks")
        }
    }
}

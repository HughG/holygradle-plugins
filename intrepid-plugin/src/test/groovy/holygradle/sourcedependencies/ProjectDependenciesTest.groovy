package holygradle.sourcedependencies

import holygradle.*
import holygradle.test.*
import org.junit.Test
import static org.junit.Assert.*

class ProjectDependenciesTest extends TestBase {
    @Test
    public void testTwoSourceDependenciesWithSharedPackedDependency() {
        def projectDir = new File(getTestDir(), "testTwoSourceDependenciesWithSharedPackedDependency")
        
        invokeGradle(projectDir) {
            forTasks("tasks")
            expectFailure(
                "Could not resolve all dependencies for configuration " + "':src_dep_with_config_foo_requiring_1_1_compile:everything'.",
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
            
        }
    }
}

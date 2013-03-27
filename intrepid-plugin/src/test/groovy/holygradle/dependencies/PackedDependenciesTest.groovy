package holygradle.dependencies

import holygradle.*
import holygradle.test.*
import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import org.gradle.api.tasks.Upload
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import static org.junit.Assert.*

class PackedDependenciesTest extends TestBase {
    @Test
    public void testConflictingModules1() {
        invokeGradle(new File(getTestDir(), "conflicting_modules1")) {
            forTasks("fetchAllDependencies")
            expectFailure(
                "Could not resolve all dependencies for configuration ':everything'.",
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
        }
    }
    
    @Test
    public void testConflictingModules2() {
        invokeGradle(new File(getTestDir(), "conflicting_modules2")) {
            forTasks("fetchAllDependencies")
            expectFailure(
                "Could not resolve all dependencies for configuration ':everything'.",
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
        }
    }
    
    @Test
    public void testUnpackingModulesToSameLocation() {
        invokeGradle(new File(getTestDir(), "unpacking_modules_to_same_location")) {
            forTasks("fetchAllDependencies")
            expectFailure(
                "Multiple different modules/versions are targetting the same location.",
                "unpacking_modules_to_same_location\\extlib' is being targetted by: [holygradle.test:example-framework:1.1, holygradle.test:external-lib:1.1].",
                "That's not going to work."
            )
        }
    }
}

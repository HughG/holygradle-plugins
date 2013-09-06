package holygradle.dependencies

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

class PackedDependenciesTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testConflictingModules1() {
        invokeGradle(new File(getTestDir(), "conflicting_modules1")) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                "Could not resolve all dependencies for configuration ':everything'.",
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
        }
    }
    
    @Test
    public void testConflictingModules2() {
        invokeGradle(new File(getTestDir(), "conflicting_modules2")) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                "Could not resolve all dependencies for configuration ':everything'.",
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
        }
    }
    
    @Test
    public void testUnpackingModulesToSameLocation() {
        invokeGradle(new File(getTestDir(), "unpacking_modules_to_same_location")) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(
                "Multiple different modules/versions are targetting the same location.",
                "unpacking_modules_to_same_location\\extlib' is being targetted by: [holygradle.test:example-framework:1.1, holygradle.test:external-lib:1.1].",
                "That's not going to work."
            )
        }
    }
}

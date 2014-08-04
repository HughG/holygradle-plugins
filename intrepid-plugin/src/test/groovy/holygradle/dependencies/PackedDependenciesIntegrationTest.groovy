package holygradle.dependencies

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.RegressionFileHelper
import holygradle.test.WrapperBuildLauncher
import org.junit.Test

class PackedDependenciesIntegrationTest extends AbstractHolyGradleIntegrationTest {
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
        final projectDir = new File(getTestDir(), "unpacking_modules_to_same_location")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
                """In root project 'unpacking_modules_to_same_location', location '${projectDir.path}\\extlib' is targeted by multiple dependencies/versions:
    holygradle.test:example-framework:1.1 in configurations [everything, bar]
        which is from packed dependency sub/../extlib
    holygradle.test:external-lib:1.1 in configurations [everything, foo, bar]
        which is from holygradle.test:example-framework:1.1
        which is from packed dependency sub/../extlib

FAILURE: Build failed with an exception.

* What went wrong:
Multiple different dependencies/versions are targeting the same locations."""
            ))
        }
    }
}

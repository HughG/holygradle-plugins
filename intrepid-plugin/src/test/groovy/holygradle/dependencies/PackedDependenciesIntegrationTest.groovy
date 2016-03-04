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
                "A conflict was found between the following modules:",
                "- holygradle.test:external-lib:1.0",
                "- holygradle.test:external-lib:1.1"
            )
        }
    }
    
    @Test
    public void testConflictingModules2() {
        final projectDir = new File(getTestDir(), "conflicting_modules2")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
                """In root project 'conflicting_modules2', location '${projectDir.absolutePath}\\extlib10' is targeted by multiple dependencies/versions:
    holygradle.test:external-lib:1.1 in configurations [bar]
        which is from packed dependency extlib10
    holygradle.test:external-lib:1.0 in configurations [foo]
        which is from packed dependency extlib10

FAILURE: Build failed with an exception.

* What went wrong:
Multiple different dependencies/versions are targeting the same locations."""
            ))
        }
    }
    
    @Test
    public void testUnpackingModulesToSameLocation() {
        final projectDir = new File(getTestDir(), "unpacking_modules_to_same_location")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
                """In root project 'unpacking_modules_to_same_location', location '${projectDir.absolutePath}\\extlib' is targeted by multiple dependencies/versions:
    holygradle.test:example-framework:1.1 in configurations [bar]
        which is from packed dependency sub/../extlib
    holygradle.test:external-lib:1.1 in configurations [foo, bar]
        which is from holygradle.test:example-framework:1.1
        which is from packed dependency sub/../extlib

FAILURE: Build failed with an exception.

* What went wrong:
Multiple different dependencies/versions are targeting the same locations."""
            ))
        }
    }
}

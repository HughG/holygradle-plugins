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
      directly from packed dependency sub/../extlib
    holygradle.test:external-lib:1.1 in configurations [foo, bar]
      directly from packed dependency extlib
      indirectly from holygradle.test:external-lib:1.1
        holygradle.test:example-framework:1.1 in configurations [bar]
          directly from packed dependency sub/../extlib

FAILURE: Build failed with an exception.

* What went wrong:
Multiple different dependencies/versions are targeting the same locations."""
            ))
        }
    }

    @Test
    public void testSameModuleVersionAtMultipleLocations() {
        final projectDir = new File(getTestDir(), "unpacking_one_version_to_many_locations")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
                    """FAILURE: Build failed with an exception.

* What went wrong:
Module version holygradle.test:external-lib:1.1 is specified by packed dependencies at both path 'sub/extlib' and """ +
                            "'extlib'.  A single version can only be specified at one path.  If you need it to appear at more than one " +
                            "location you can explicitly create links."
            ))
        }
    }
}

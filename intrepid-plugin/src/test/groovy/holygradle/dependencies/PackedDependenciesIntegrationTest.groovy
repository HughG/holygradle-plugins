package holygradle.dependencies

import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.RegressionFileHelper
import holygradle.test.WrapperBuildLauncher
import org.junit.Ignore
import org.junit.Test

class PackedDependenciesIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testConflictingModules1() {
        invokeGradle(new File(getTestDir(), "conflicting_modules1")) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RegressionFileHelper.toStringWithPlatformLineBreaks(
                """A conflict was found between the following modules:
  - holygradle.test:external-lib:1.0
  - holygradle.test:external-lib:1.1
"""))
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
            launcher.expectFailure("""Multiple different dependencies/versions are targeting the same locations.""")
        }
    }

    @Test
    public void testSameModuleVersionInSameConfigurationAtMultipleLocations() {
        final projectDir = new File(getTestDir(), "same_module_same_conf_multi_target")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            // This used to be disallowed but refactoring while developing source overrides, to allow multiple parents,
            // means it works now.
        }
    }
    @Test
    public void testSameModuleVersionInDifferentConfigurationsAtMultipleLocations() {
        final projectDir = new File(getTestDir(), "same_module_diff_conf_multi_target")
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            // This should work, because they're in different configurations.
        }
    }
}

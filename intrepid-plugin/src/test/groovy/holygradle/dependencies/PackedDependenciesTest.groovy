package holygradle.dependencies

import holygradle.*
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
    public void testConflictingModules() {
        def projectDir = new File(getTestDir(), "conflicting_modules")
        invokeTaskExpectingFailure(
            projectDir,
            "fetchAllDependencies", 
            "Could not resolve all dependencies for configuration ':everything'.",
            "A conflict was found between the following modules:",
            "- holygradle.test:external-lib:1.0",
            "- holygradle.test:external-lib:1.1"
        )
    }
    
    @Test
    public void testUnpackingModulesToSameLocation() {
        def projectDir = new File(getTestDir(), "unpacking_modules_to_same_location")
        invokeTaskExpectingFailure(
            projectDir,
            "fetchAllDependencies", 
            "Could not resolve all dependencies for configuration ':everything'.",
            "A conflict was found between the following modules:",
            "- holygradle.test:external-lib:1.0",
            "- holygradle.test:external-lib:1.1"
        )
    }
}

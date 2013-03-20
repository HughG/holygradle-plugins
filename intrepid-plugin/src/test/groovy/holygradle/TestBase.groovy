package holygradle

import org.junit.Test
import java.io.File
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import static org.junit.Assert.*

class TestBase {
    protected RegressionFileHelper regression
    
    TestBase() {
        regression = new RegressionFileHelper(this)
    }
    
    protected File getTestDir() {
        return new File("src/test/groovy/" + getClass().getName().replace(".", "/"))
    }
    
    protected void invokeTask(File projectDir, String taskName) {
        GradleConnector connector = GradleConnector.newConnector()
        connector.forProjectDirectory(projectDir)
        ProjectConnection connection = connector.connect()
        try { 
            BuildLauncher launcher = connection.newBuild()
            launcher.forTasks(taskName)
            launcher.run()
        } finally {
            connection.close()
        }
    }
    
    protected void invokeTaskExpectingFailure(File projectDir, String taskName, String... errorMessages) {
        GradleConnector connector = GradleConnector.newConnector()
        connector.forProjectDirectory(projectDir)
        ProjectConnection connection = connector.connect()
        try {
            def errorOutput = new ByteArrayOutputStream()
            BuildLauncher launcher = connection.newBuild()
            launcher.forTasks("fetchAllDependencies")
            launcher.setStandardError(errorOutput)
            def error = null
            try {
                launcher.run()
            } catch (RuntimeException e) {
                error = errorOutput.toString()
            }
            if (error == null) {
                fail("Expected failure when running '${taskName}' in '${projectDir}' but there was none.")
            }
            errorMessages.each {
                assertTrue("Error message should contain '${it}' but it contained: ${error}.", error.contains(it))
            }
        } finally {
            connection.close()
        }
    }
}
package holygradle.test

import org.junit.Test
import java.io.File
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.util.ConfigureUtil
import static org.junit.Assert.*

class TestBase {
    protected RegressionFileHelper regression
    
    TestBase() {
        regression = new RegressionFileHelper(this)
    }
    
    protected File getTestDir() {
        return new File("src/test/groovy/" + getClass().getName().replace(".", "/"))
    }
    
    protected void invokeGradle(File projectDir, Closure closure) {
        GradleConnector connector = GradleConnector.newConnector()
        connector.forProjectDirectory(projectDir)
        ProjectConnection connection = connector.connect()
        try { 
            BuildLauncher launcher = new WrapperBuildLauncher(connection.newBuild())
            ConfigureUtil.configure(closure, launcher)
            
            if (launcher.expectedFailures.size() == 0) {
                launcher.run()
            } else {
                def errorOutput = new ByteArrayOutputStream()
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
                launcher.expectedFailures.each {
                    assertTrue("Error message should contain '${it}' but it contained: ${error}.", error.contains(it))
                }
            }
        } finally {
            connection.close()
        }
    }
    
}
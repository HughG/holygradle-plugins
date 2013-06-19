package holygradle.test

import org.junit.Test

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
    /**
     * Invokes Gradle for a specified project directory.  A closure can be used to configure the launcher used to run
     * Gradle; for example, to set the command line arguments.
     *
     * @param projectDir The directory containing the gradle project to use.
     * @param closure A closure to configure a {@link WrapperBuildLauncher}.
     */
    protected void invokeGradle(File projectDir, Closure closure) {
        GradleConnector connector = GradleConnector.newConnector()
        connector.forProjectDirectory(projectDir)

        maybeForwardGradleHomeInfo(connector)

        ProjectConnection connection = connector.connect()
        try { 
            BuildLauncher launcher = new WrapperBuildLauncher(connection.newBuild())
            maybeForwardHttpProxyProperties(launcher)
            ConfigureUtil.configure(closure, launcher)

            if (launcher.expectedFailures.size() == 0) {
                launcher.run()
            } else {
                OutputStream errorOutput = new ByteArrayOutputStream()
                launcher.setStandardError(errorOutput)
                String error = null
                try {
                    launcher.run()
                } catch (RuntimeException e) {
                    error = errorOutput.toString()
                }
                if (error == null) {
                    fail("Expected failure but there was none.")
                }
                launcher.expectedFailures.each {
                    assertTrue("Error message should contain '${it}' but it contained: ${error}.", error.contains(it))
                }
            }
        } finally {
            connection.close()
        }
    }

    /**
     * Pass on Gradle's user home and installation home if the user has one or both configured.
     * @param connector The connector to be configured.
     */
    private void maybeForwardGradleHomeInfo(GradleConnector connector) {
        final String gradleUserHome = System.getProperty("holygradle.gradleUserHomeDir")
        if (gradleUserHome != null) {
            connector.useGradleUserHomeDir(new File(gradleUserHome))
        }
        final String gradleHome = System.getProperty("holygradle.gradleHomeDir")
        if (gradleHome != null) {
            connector.useInstallation(new File(gradleHome))
        }
    }

    /**
     * If this process has the system properties {@code http.proxyHost} and/or {@code http.proxyPort} set, this method
     * adds them to the JVM properties of the {@link BuildLauncher}.
     * @param launcher The launcher to be configured.
     */
    private void maybeForwardHttpProxyProperties(BuildLauncher launcher) {
        String[] jvmArguments = []
        String proxyHost = System.getProperty("http.proxyHost")
        String proxyPort = System.getProperty("http.proxyPort")
        if (proxyHost != null) {
            jvmArguments << proxyHost
        }
        if (proxyPort != null) {
            jvmArguments << proxyPort
        }
        if (!jvmArguments.empty) {
            launcher.setJvmArguments(jvmArguments)
        }
    }

}
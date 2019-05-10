package holygradle.test

import org.gradle.api.tasks.TaskExecutionException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.util.ConfigureUtil

import java.util.concurrent.TimeUnit

import static org.junit.Assert.*

class AbstractHolyGradleIntegrationTest extends AbstractHolyGradleTest {
    protected final File customInitScript;
    protected final File gradleUserHome;
    protected final URI pluginsRepoOverride;

    /**
     * This Java system property key must match the one in test.gradle, in the root project.  It must be supplied, and
     * its value identifies the path to the custom gradle init script. The custom gradle script applies the holygradle
     * plugins to the project hence is required to be provided for projects which intend to use the holygradle plug-ins
     */
    private static final String CUSTOM_INIT_SCRIPT_SYSTEM_PROPERTY_KEY = "holygradle.customInitScriptPath"
    /**
     * This Java system property key must match the one in test.gradle, in the root project.  It must be supplied, and
     * its value identifies the local folder to be used as the "Gradle user home" for integration tests.  This is to
     * keep it separate from the user's normal location, for ease of debugging and clean-up.  (It is used by this class
     * when it is run by JUnit, directly from a Gradle task in the test.gradle file.  It's not used by the launched
     * build process or the scripts it runs.)
     */
    private static final String GRADLE_USER_HOME_SYSTEM_PROPERTY_KEY = "holygradle.gradleUserHomeForIntegrationTest"
    /**
     * This Java system property key must match the one in test.gradle, in the root project.  It must be supplied, and
     * its value identifies the (file) URI at which to find the Holy Gradle plugins.  The init script
     * holy-gradle-init.gradle in the custom distribution uses the value of this property if it's defined.  Note that,
     * assuming the value value is indeed a local file, Gradle will not download the plugin JARs to the Gradle user
     * home, rather it will use them in place.
     */
    private static final String PLUGINS_REPO_OVERRIDE_SYSTEM_PROPERTY_KEY = "holygradle.pluginsRepoOverride"
    /**
     * This Java system property key must match the one in test.gradle, in the root project.  It is optional, and its
     * value is used to supply extra arguments to the Gradle daemon session launched for unit testing.  The string
     * should be in the usual form for the Gradle command line, as described by "gw --help"; but, only those matching
     * {@link org.gradle.StartParameter} can be used; e.g., "--debug".  (It is used by this class when it is run by
     * JUnit, directly from a Gradle task in the test.gradle file.  It's not used by the launched build process or the
     * scripts it runs.)
     */
    private static final String EXTRA_ARGS_SYSTEM_PROPERTY_KEY = "holygradle.extraArgsForIntegrationTest"

    private static String getRequiredSystemProperty(String name) {
        final String value = System.getProperty(name)
        if (value == null) {
            throw new RuntimeException(
                "Property '${name}' must be defined to run integration tests. " +
                "Please ensure you have run this test using a build.gradle file which sets it."
            )
        }
        return value
    }

    AbstractHolyGradleIntegrationTest() {
        customInitScript = new File(getRequiredSystemProperty(CUSTOM_INIT_SCRIPT_SYSTEM_PROPERTY_KEY))
        gradleUserHome = new File(getRequiredSystemProperty(GRADLE_USER_HOME_SYSTEM_PROPERTY_KEY))
        pluginsRepoOverride = new URI(getRequiredSystemProperty(PLUGINS_REPO_OVERRIDE_SYSTEM_PROPERTY_KEY))
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
        connector
            .forProjectDirectory(projectDir)
            .useGradleUserHomeDir(gradleUserHome)
        // Set a short idle timeout, so that the daemon stops soon after the test.  This is in case the custom
        // distribution we're testing changes before the next test run.  In that case, we need to delete the unpacked
        // version from the Gradle user home, but we can't do that if the daemon still has a lock on the files.
        ((DefaultGradleConnector)connector).daemonMaxIdleTime(3, TimeUnit.SECONDS)

        ProjectConnection connection = connector.connect()
        try {
            WrapperBuildLauncher launcher = new WrapperBuildLauncher(connection.newBuild())
            launcher.standardOutput = System.out
            launcher.standardError = System.err
            maybeAddHttpProxyArguments(launcher)
            // Use custom init script.
            launcher.addArguments("-I", customInitScript.toString())
            // Access plugins from a local repo.
            launcher.addArguments("-D${PLUGINS_REPO_OVERRIDE_SYSTEM_PROPERTY_KEY}=${pluginsRepoOverride.path}")
            // Show full stacktrace in case of error.
            launcher.addArguments("-S")
            maybeAddExtraArguments(launcher)

            ConfigureUtil.configure(closure, launcher)

            if (launcher.expectedFailures.size() == 0) {
                launcher.run()
            } else {
                OutputStream errorOutput = new ByteArrayOutputStream()
                launcher.setStandardError(errorOutput)
                String error = null
                try {
                    launcher.run()
                } catch (TaskExecutionException e) {
                    if (e.cause?.message?.startsWith("Could not install Gradle distribution from")) {
                        println "Failed to install base Gradle distribution."
                        println "Try re-running tests with proxy arguments: " +
                            "-Dhttp.proxyHost=xxx -Dhttp.proxyPort=NNNN " +
                            "-Dhttps.proxyHost=xxx -Dhttps.proxyPort=NNNN"
                    }
                } catch (RuntimeException e) {
                    //Print stack trace to stderr
                    e.printStackTrace(System.err)

                    // Store Exception message as String
                    StringWriter errorStringWriter = new StringWriter()
                    e.printStackTrace(new PrintWriter(errorStringWriter))
                    error = errorStringWriter.toString()
                }
                if (error == null) {
                    fail("Expected failure but there was none.")
                }
                System.err.println("ERROR: ${error}")
                launcher.expectedFailures.each {
                    assertTrue("Error message should contain '${it}' but it contained: ${error}.", error.contains(it))
                }
            }
        } finally {
            connection.close()
        }
    }

    /**
     * If this process has the system property named {@code property}, this method adds it to the JVM properties of the
     * {@link BuildLauncher}.
     *
     * @param launcher The launcher to be configured.
     */
    private static void maybeAddSystemProperty(WrapperBuildLauncher launcher, String property) {
        String value = System.getProperty(property)
        if (value != null) {
            launcher.addArguments("-D${property}=${value}")
        }
    }


    /**
     * If this process has the system properties {@code http.proxyHost} and/or {@code http.proxyPort} set, this method
     * adds them to the JVM properties of the {@link BuildLauncher}.
     *
     * @param launcher The launcher to be configured.
     */
    private static void maybeAddHttpProxyArguments(WrapperBuildLauncher launcher) {
        maybeAddSystemProperty(launcher, "http.proxyHost")
        maybeAddSystemProperty(launcher, "http.proxyPort")
        maybeAddSystemProperty(launcher, "https.proxyHost")
        maybeAddSystemProperty(launcher, "https.proxyPort")
    }

    /**
     * If the process has the system property defined by
     * {@link AbstractHolyGradleIntegrationTest#EXTRA_ARGS_SYSTEM_PROPERTY_KEY}, use its value (from which any leading
     * and trailing quotes will automatically have been stripped off) as a string of extra arguments to pass to the test
     * run daemon instance.
     *
     * @param launcher The launcher to be configured.
     */
    private static void maybeAddExtraArguments(WrapperBuildLauncher launcher) {
        final String extraArgs = System.getProperty(EXTRA_ARGS_SYSTEM_PROPERTY_KEY)
        if (extraArgs != null) {
            launcher.addArguments(extraArgs)
        }
    }
}
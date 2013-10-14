package holygradle.test;

import org.gradle.tooling.*;
import org.gradle.tooling.model.*;

import java.io.*;
import java.util.*;

/**
 * Wraps a {@link BuildLauncher} and adds some extra state and methods for testing, for use with
 * {@link AbstractHolyGradleIntegrationTest}.
 * @see AbstractHolyGradleIntegrationTest
 */
public class WrapperBuildLauncher implements BuildLauncher {
    private BuildLauncher launcher;
    private List<String> expectedFailures = new LinkedList<String>();
    private List<String> arguments = new LinkedList<String>();

    public WrapperBuildLauncher(BuildLauncher launcher) {
        this.launcher = launcher;
    }
    
    public BuildLauncher addProgressListener(ProgressListener listener) {
        launcher.addProgressListener(listener);
        return this;
    }
    public BuildLauncher forTasks(Iterable<? extends Task> tasks) {
        launcher.forTasks(tasks);
        return this;
    }
    public BuildLauncher forTasks(String... tasks) {
        launcher.forTasks(tasks);
        return this;
    }
    public BuildLauncher forTasks(Task... tasks) {
        launcher.forTasks(tasks);
        return this;
    }
    public void run() {
        forwardAddedArguments();
        launcher.run();
    }
    public void run(ResultHandler<? super Void> handler) {
        forwardAddedArguments();
        launcher.run(handler);
    }
    public BuildLauncher setJavaHome(File javaHome) {
        launcher.setJavaHome(javaHome);
        return this;
    }
    public BuildLauncher setJvmArguments(String... jvmArguments) {
        launcher.setJvmArguments(jvmArguments);
        return this;
    }
    public BuildLauncher setStandardError(OutputStream outputStream) {
        launcher.setStandardError(outputStream);
        return this;
    }
    public BuildLauncher setStandardInput(InputStream inputStream) {
        launcher.setStandardInput(inputStream);
        return this;
    }
    public BuildLauncher setStandardOutput(OutputStream outputStream) {
        launcher.setStandardOutput(outputStream);
        return this;
    }

    /**
     * Always throws {@link java.lang.UnsupportedOperationException}; use {@link #addArguments(String...)} instead.
     * @param arguments Gradle command line arguments
     * @return this
     */
    public BuildLauncher withArguments(String... arguments) {
        launcher.withArguments(arguments);
        return this;
    }

    /**
     * Adds expected error messages to the collection returned by {@link #expectFailure(String...)}.
     * {@link AbstractHolyGradleIntegrationTest#invokeGradle(java.io.File, groovy.lang.Closure)} uses this to check for
     * error messages when an invoked instance of Gradle has finished.
     * @param messages The messages to add.
     * @return this
     */
    public BuildLauncher expectFailure(String... messages) {
        this.expectedFailures.addAll(Arrays.asList(messages));
        return this;
    }

    /**
     * Returns the list of expected failures which have been added by calls to {@link #expectFailure(String...)}.
     * @return The list of expected failures.
     */
    public List<String> getExpectedFailures() {
        return expectedFailures;
    }

    /**
     * Adds to the list of command build line arguments.  All arguments passed to all calls of this method on an
     * instance will be concatenated and passed to the wrapped launcher's {@link #withArguments(String...)} method when
     * {@link #run()} (or {@link #run(org.gradle.tooling.ResultHandler)}) is called.
     * @param arguments Gradle command line arguments
     * @return this
     */
    public BuildLauncher addArguments(String... arguments) {
        Collections.addAll(this.arguments, arguments);
        return this;
    }

    private void forwardAddedArguments() {
        String[] arguments = this.arguments.toArray(new String[this.arguments.size()]);
        System.out.println("Running with arguments " + this.arguments.toString());
        launcher.withArguments(arguments);
    }
}
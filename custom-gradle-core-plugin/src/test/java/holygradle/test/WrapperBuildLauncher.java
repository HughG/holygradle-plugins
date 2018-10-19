package holygradle.test;

import org.gradle.internal.impldep.com.amazonaws.services.kms.model.UnsupportedOperationException;
import org.gradle.tooling.*;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.events.*;
import org.gradle.tooling.model.*;

import java.io.*;
import java.util.*;

/**
 * Wraps a {@link BuildLauncher} and adds some extra state and methods for testing, for use with
 * {@link AbstractHolyGradleIntegrationTest}.
 * @see AbstractHolyGradleIntegrationTest
 */
public class WrapperBuildLauncher implements BuildLauncher {
    private final BuildLauncher launcher;
    private final List<String> expectedFailures = new LinkedList<String>();
    private final List<String> arguments = new LinkedList<String>();

    public WrapperBuildLauncher(BuildLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public WrapperBuildLauncher addProgressListener(ProgressListener listener) {
        launcher.addProgressListener(listener);
        return this;
    }

    @Override
    public WrapperBuildLauncher addProgressListener(org.gradle.tooling.events.ProgressListener progressListener) {
        launcher.addProgressListener(progressListener);
        return this;
    }

    @Override
    public WrapperBuildLauncher addProgressListener(
        org.gradle.tooling.events.ProgressListener progressListener,
        Set<OperationType> set
    ) {
        launcher.addProgressListener(progressListener, set);
        return this;
    }

    @Override
    public WrapperBuildLauncher addProgressListener(
        org.gradle.tooling.events.ProgressListener progressListener,
        OperationType... operationTypes
    ) {
        launcher.addProgressListener(progressListener, operationTypes);
        return this;
    }

    @Override
    public WrapperBuildLauncher withCancellationToken(CancellationToken cancellationToken) {
        launcher.withCancellationToken(cancellationToken);
        return this;
    }

    @Override
    public WrapperBuildLauncher forTasks(Iterable<? extends Task> tasks) {
        launcher.forTasks(tasks);
        return this;
    }

    @Override
    public WrapperBuildLauncher forLaunchables(Launchable... launchables) {
        launcher.forLaunchables(launchables);
        return this;
    }

    @Override
    public WrapperBuildLauncher forLaunchables(Iterable<? extends Launchable> iterable) {
        launcher.forLaunchables(iterable);
        return this;
    }

    @Override
    public WrapperBuildLauncher forTasks(String... tasks) {
        launcher.forTasks(tasks);
        return this;
    }

    @Override
    public WrapperBuildLauncher forTasks(Task... tasks) {
        launcher.forTasks(tasks);
        return this;
    }

    @Override
    public void run() {
        forwardAddedArguments();
        launcher.run();
    }

    @Override
    public void run(ResultHandler<? super Void> handler) {
        forwardAddedArguments();
        launcher.run(handler);
    }

    @Override
    public WrapperBuildLauncher setJavaHome(File javaHome) {
        launcher.setJavaHome(javaHome);
        return this;
    }

    @Override
    public WrapperBuildLauncher setJvmArguments(String... jvmArguments) {
        launcher.setJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public WrapperBuildLauncher setJvmArguments(Iterable<String> iterable) {
        launcher.setJvmArguments(iterable);
        return this;
    }

    @Override
    public WrapperBuildLauncher setEnvironmentVariables(Map<String, String> var1) {
        launcher.setEnvironmentVariables(var1);
        return this;
    }


    @Override
    public WrapperBuildLauncher setStandardError(OutputStream outputStream) {
        launcher.setStandardError(outputStream);
        return this;
    }

    @Override
    public WrapperBuildLauncher setColorOutput(boolean b) {
        launcher.setColorOutput(b);
        return this;
    }

    @Override
    public WrapperBuildLauncher setStandardInput(InputStream inputStream) {
        launcher.setStandardInput(inputStream);
        return this;
    }

    @Override
    public WrapperBuildLauncher setStandardOutput(OutputStream outputStream) {
        launcher.setStandardOutput(outputStream);
        return this;
    }

    /**
     * Always throws {@link java.lang.UnsupportedOperationException}; use {@link #addArguments(String...)} instead.
     * @param arguments Gradle command line arguments
     * @return this
     */
    @Override
    public WrapperBuildLauncher withArguments(String... arguments) {
        throw new UnsupportedOperationException(
            "Call WrapperBuildLauncher#addArguments instead of BuildLauncher#withArguments"
        );
    }

    /**
     * Always throws {@link java.lang.UnsupportedOperationException}; use {@link #addArguments(String...)} instead.
     * @param iterable Gradle command line arguments
     * @return this
     */
    @Override
    public WrapperBuildLauncher withArguments(Iterable<String> iterable) {
        throw new UnsupportedOperationException(
                "Call WrapperBuildLauncher#addArguments instead of BuildLauncher#withArguments"
        );
    }

    /**
     * Adds expected error messages to the collection returned by {@link #getExpectedFailures()}.
     * {@link AbstractHolyGradleIntegrationTest#invokeGradle(java.io.File, groovy.lang.Closure)} uses this to check for
     * error messages when an invoked instance of Gradle has finished.
     * @param messages The messages to add.
     * @return this
     */
    public WrapperBuildLauncher expectFailure(String... messages) {
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
    public WrapperBuildLauncher addArguments(String... arguments) {
        Collections.addAll(this.arguments, arguments);
        return this;
    }

    private void forwardAddedArguments() {
        String[] arguments = this.arguments.toArray(new String[this.arguments.size()]);
        System.out.println("Running with arguments " + this.arguments.toString());
        launcher.withArguments(arguments);
    }
}
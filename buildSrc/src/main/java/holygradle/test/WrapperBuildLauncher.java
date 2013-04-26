package holygradle.test;
import org.gradle.api.*;
import org.gradle.tooling.*;
import org.gradle.tooling.model.Task;
import java.io.*;
import java.util.*;
class WrapperBuildLauncher implements BuildLauncher {
    private BuildLauncher launcher;
    public List<String> expectedFailures = new LinkedList<String>();
    
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
        launcher.run();
    }
    public void run(ResultHandler<? super Void> handler) {
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
    public BuildLauncher withArguments(String... arguments) {
        launcher.withArguments(arguments);
        return this;
    }
    
    public BuildLauncher expectFailure(String... messages) {
        this.expectedFailures.addAll(Arrays.asList(messages));
        return this;
    }
}
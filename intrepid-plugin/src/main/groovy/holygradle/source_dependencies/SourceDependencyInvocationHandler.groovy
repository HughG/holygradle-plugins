package holygradle.source_dependencies

/**
 * Gradle DSL handler for an individual task invocation in a {@link SourceDependencyTaskHandler} block; that is, for a
 * call to {@link SourceDependencyTaskHandler#invoke(java.lang.String[])}.  This describes a command line to be executed, and the
 * way in which its exit code should be handled, which defaults to
 * {@link SourceDependencyInvocationHandler.ExitCodeBehaviour#FAIL_IMMEDIATELY}.
 */
class SourceDependencyInvocationHandler {
    /**
     * Enumeration of ways to handle the exit codes of processes run by a {@link SourceDependencyTask} due to calls to
     * {@link SourceDependencyTaskHandler#invoke(java.lang.String[])}.
     */
    public static enum ExitCodeBehaviour {
        /** Ignore failures, executing all tasks and reporting success at the end. */
        IGNORE_FAILURE,
        /** Respect failures, ending execution of all tasks as soon as one ends. */
        FAIL_IMMEDIATELY,
        /** Execute all tasks then, if any failed, report all failures at the end and cause the root task to fail. */
        FAIL_AT_END
    }

    /**
     * An unmodifiable collection containing the command line to be executed, with the executable name as the first item.
     */
    public final Collection<String> cmdLine

    private ExitCodeBehaviour exitCodeBehaviour = ExitCodeBehaviour.FAIL_IMMEDIATELY

    /**
     * Constructs a new instance of {@link SourceDependencyInvocationHandler} for a given command line.
     * @param cmdLine The command line to execute; the first item is the executable and the rest are arguments.
     */
    public SourceDependencyInvocationHandler(String... cmdLine) {
        this.cmdLine = Collections.unmodifiableCollection((Collection<String>)cmdLine)
    }

    /** Sets the exit code handling to {@link SourceDependencyInvocationHandler.ExitCodeBehaviour#IGNORE_FAILURE}. */
    public void ignoreFailure() {
        exitCodeBehaviour = ExitCodeBehaviour.IGNORE_FAILURE
    }

    /** Sets the exit code handling to {@link SourceDependencyInvocationHandler.ExitCodeBehaviour#FAIL_IMMEDIATELY}. */
    public void failImmediately() {
        exitCodeBehaviour = ExitCodeBehaviour.FAIL_IMMEDIATELY
    }

    /** Sets the exit code handling to {@link SourceDependencyInvocationHandler.ExitCodeBehaviour#FAIL_AT_END}. */
    public void failAtEnd() {
        exitCodeBehaviour = ExitCodeBehaviour.FAIL_AT_END
    }

    /**
     * Returns the exit code behaviour set for this instance.
     * @return The exit code behaviour set for this instance.
     * */
    public ExitCodeBehaviour getExitCodeBehaviour() {
        return exitCodeBehaviour
    }

    /**
     * Returns a human-readable description of this command invocation.
     * @return A human-readable description of this command invocation.
     */
    public String getDescription() {
        "'" + cmdLine.join(" ") + "'"
    }
}
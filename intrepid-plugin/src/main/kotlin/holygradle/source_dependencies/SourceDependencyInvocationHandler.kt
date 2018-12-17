package holygradle.source_dependencies

import java.util.*

/**
 * Gradle DSL handler for an individual task invocation in a {@link SourceDependencyTaskHandler} block; that is, for a
 * call to {@link SourceDependencyTaskHandler#invoke(java.lang.String[])}.  This describes a command line to be executed, and the
 * way in which its exit code should be handled, which defaults to
 * {@link SourceDependencyInvocationHandler.ExitCodeBehaviour#FAIL_IMMEDIATELY}.
 */
open class SourceDependencyInvocationHandler(vararg cmdLine: String) {
    /**
     * Enumeration of ways to handle the exit codes of processes run by a {@link SourceDependencyTask} due to calls to
     * {@link SourceDependencyTaskHandler#invoke(java.lang.String[])}.
     */
    enum class ExitCodeBehaviour {
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
    val cmdLine: List<String> = Collections.unmodifiableList(cmdLine.toList())

    /**
     * The exit code behaviour set for this instance.
     */
    var exitCodeBehaviour = ExitCodeBehaviour.FAIL_IMMEDIATELY
        private set

    /** Sets the exit code handling to {@link SourceDependencyInvocationHandler.ExitCodeBehaviour#IGNORE_FAILURE}. */
    fun ignoreFailure() {
        exitCodeBehaviour = ExitCodeBehaviour.IGNORE_FAILURE
    }

    /** Sets the exit code handling to {@link SourceDependencyInvocationHandler.ExitCodeBehaviour#FAIL_IMMEDIATELY}. */
    fun failImmediately() {
        exitCodeBehaviour = ExitCodeBehaviour.FAIL_IMMEDIATELY
    }

    /** Sets the exit code handling to {@link SourceDependencyInvocationHandler.ExitCodeBehaviour#FAIL_AT_END}. */
    fun failAtEnd() {
        exitCodeBehaviour = ExitCodeBehaviour.FAIL_AT_END
    }

    /**
     * A human-readable description of this command invocation.
     */
    val description: String
        get() = cmdLine.joinToString(prefix = "'", separator = " ", postfix = "'")
}
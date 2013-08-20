package holygradle.source_dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

/**
 * Task subclass which launches an executable for a source dependency in a multi-project build hierarchy.  Instances of
 * this class will be created by {@link SourceDependencyTaskHandler} for each source dependency in the project hierarchy.
 *
 * If an instance of this class is the root of the source dependency task hierarchy (that is, if no other {@link
 * SourceDependencyTask} instances depend on it) then it will do the "fail at end" check after all the tasks it depends
 * on have executed, if it is initialized with an instance of {@link SourceDependencyInvocationHandler} on which
 * {@link SourceDependencyInvocationHandler#failAtEnd()} has been called.  See that class' documentation for more
 * information on how instance of this task are constructed, and the dependency relationships between them.
 *
 * @see SourceDependencyTaskHandler
 * @see SourceDependencyInvocationHandler
 */
class SourceDependencyTask extends DefaultTask {
    /**
     * True if and only if this task has no other {@link SourceDependencyTask}s which depend on it.  This property is
     * only correctly maintained if dependent {@link SourceDependencyTask}s are added by calling
     * {@link SourceDependencyTask#addDependentTask(holygradle.source_dependencies.SourceDependencyTask)}.
     */
    public boolean isRootTask = true
    /** Holds information on the command to execute, and how to handle its exit code. */
    private SourceDependencyInvocationHandler invocation
    /** Holds the result of executing the command. */
    private ExecResult execResult = null

    /**
     * Returns the result of the execution of the command.  The value is undefined before the task has been executed.
     * @return The result of the execution of the command.
     */
    public ExecResult getExecResult() {
        return execResult
    }

    /**
     * Adds a dependency on task {@code t} from some sub-project, which marks {@code t} as not a "root task", meaning
     * its {@link #maybeFailAtEnd()} method will do nothing, deferring to this "parent" task (or, to the root task).
     *
     * This is different from simply adding a dependency on {@code t}.  A {@link SourceDependencyTask} which has
     * multiple calls to {@link SourceDependencyTaskHandler#invoke(String...)} will also add dependent tasks for each
     * invocation call, but using the {@link #dependsOn(Object...)} method directly, so that their {@link #isRootTask}
     * status is unaffected. (NOTE 2013-08-19 HughG: On reflection, that may be a bug.  Should think about and carefully
     * document the failure path for multi-invocation tasks across multiple projects.)
     *
     * @param t A {@link SourceDependencyTask} from a sub-project.
     */
    public void addDependentTask(SourceDependencyTask t) {
        dependsOn(t)
        t.isRootTask = false
    }

    /**
     * This is only public so that it can be called internally in a closure; don't call this method from outside.
     */
    public Collection<SourceDependencyTask> collectDependentTasks() {
        Collection<SourceDependencyTask> taskCollection = new ArrayList<SourceDependencyTask>()
        taskCollection.add(this)
        
        getDependsOn().each { t ->
            if (t instanceof SourceDependencyTask) {
                t.collectDependentTasks().each {
                    taskCollection.add(it)
                }
            }
        }
        return taskCollection.unique()
    }

    /*
     * Builds a map from tasks to execution results, for tasks whose commands had a non-zero exit code.
     */
    private Map<Task, ExecResult> collectFailures() {
        Map<Task, ExecResult> allFailures = [:]
        collectDependentTasks().each { SourceDependencyTask t ->
            if (t.getExecResult().getExitValue() != 0) {
                allFailures[t] = t.getExecResult()
            }
        }
        return allFailures
    }

    /**
     * This is only public so that it can be called internally in a closure; don't call this method from outside.
     */
    public void maybeFailAtEnd() {
        if (invocation != null &&
            invocation.exitCodeBehaviour == SourceDependencyInvocationHandler.ExitCodeBehaviour.FAIL_AT_END &&
            isRootTask
        ) {
            Map<Task, ExecResult> allFailures = collectFailures()
            String failureMessage = null
            if (allFailures.size() == 1) {
                failureMessage = "There was a failure while running ${invocation.description}."
            } else if (allFailures.size() > 1) {
                failureMessage = "There were ${allFailures.size()} failures while running ${invocation.description}."
            }
            println failureMessage
            allFailures.each { failedTask, failureResult ->
                println "    ${failedTask.project.name} failed due to exit code: ${failureResult.getExitValue()}"
            }
            if (allFailures.size() > 0) {
                throw new RuntimeException(failureMessage)
            }
        }
    }

    /*
     * Adds a doLast closure to handle the "fail at end" behaviour.
     */
    private void initializeInternal() {
        doLast { SourceDependencyTask it ->
            it.maybeFailAtEnd()
        }
    }

    /**
     * Initializes the task with an invocation handler, which describes the command to execute and how its exit code
     * should be handled.
     * @param invocation An invocation handler.
     */
    public void initialize(SourceDependencyInvocationHandler invocation) {
        this.invocation = invocation

        doLast { SourceDependencyTask it ->
            println "Invoking ${invocation.getDescription()} on '${project.name}'..."
            execResult = project.exec { ExecSpec spec ->
                spec.commandLine = invocation.cmdLine
                spec.ignoreExitValue =
                    invocation.exitCodeBehaviour != SourceDependencyInvocationHandler.ExitCodeBehaviour.FAIL_IMMEDIATELY
            }
            if (invocation.exitCodeBehaviour == SourceDependencyInvocationHandler.ExitCodeBehaviour.FAIL_IMMEDIATELY) {
                execResult.assertNormalExitValue()
            }
            println ""
        }

        initializeInternal()
    }

    /**
     * Initializes the task with multiple invocation handlers, each describing the command to execute and how its exit
     * code should be handled, along with a task which will execute the command.
     * @param invocationsWithTasks An ordered collection of invocation descriptions and tasks which implement them.
     */
    public void initialize(LinkedHashMap<SourceDependencyInvocationHandler, Task> invocationsWithTasks) {
        group = "Source Dependencies"
        String cmdLineDescription = invocationsWithTasks.collect { it.key.description }.join(", ")
        description = "Invoke ${cmdLineDescription} on '${project.name}' and dependencies."

        invocationsWithTasks.each { i, Task t ->
            dependsOn(t)
        }
        
        initializeInternal()
    }
}

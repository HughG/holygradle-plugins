package holygradle.source_dependencies

import holygradle.util.unique
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

// TODO 2017-07-29: HughG: Refactor to a sealed class with "root" and "sub-task" subclasses?
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
open class SourceDependencyTask : DefaultTask() {
    /**
     * True if and only if this task has no other {@link SourceDependencyTask}s which depend on it.  This property is
     * only correctly maintained if dependent {@link SourceDependencyTask}s are added by calling
     * {@link SourceDependencyTask#addDependentTask(holygradle.source_dependencies.SourceDependencyTask)}.
     */
    private var isRootTask = true
    /** Holds information on the command to execute, and how to handle its exit code. */
    private var invocation: SourceDependencyInvocationHandler? = null
    /** The result of executing the command. The value is undefined before the task has been executed. */
    var execResult: ExecResult? = null
        private set

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
    fun addDependentTask(t: SourceDependencyTask) {
        dependsOn(t)
        t.isRootTask = false
    }

    private fun collectDependentTasks(): Collection<SourceDependencyTask> {
        val taskCollection = mutableListOf<SourceDependencyTask>()
        taskCollection.add(this)

        dependsOn.filterIsInstance<SourceDependencyTask>().flatMapTo(taskCollection) { it.collectDependentTasks() }
        return taskCollection.unique()
    }

    /*
     * Builds a map from tasks to execution results, for tasks whose commands had a non-zero exit code.
     */
    private fun collectFailures(): Map<Task, ExecResult> {
        val allFailures = mutableMapOf<Task, ExecResult>()
        collectDependentTasks().forEach { t ->
            val execResult = t.execResult
            if (execResult != null && execResult.exitValue != 0) {
                allFailures[t] = execResult
            }
        }
        return allFailures
    }

    /**
     * This is only public so that it can be called internally in a closure; don't call this method from outside.
     */
    fun maybeFailAtEnd() {
        val invocation1 = invocation
        if (invocation1 != null &&
            invocation1.exitCodeBehaviour == SourceDependencyInvocationHandler.ExitCodeBehaviour.FAIL_AT_END &&
            isRootTask
        ) {
            val allFailures = collectFailures()
            val failureMessage: String = when {
                allFailures.size == 1 -> "There was a failure while running ${invocation1.description}."
                allFailures.size > 1 -> "There were ${allFailures.size} failures while running ${invocation1.description}."
                else -> "There were one or more failures while running ${invocation1.description} but no error messages were recorded."
            }
            println(failureMessage)
            for ((failedTask, failureResult) in allFailures) {
                println("    ${failedTask.project.name} failed due to exit code: ${failureResult.exitValue}")
            }
            if (allFailures.isNotEmpty()) {
                throw RuntimeException(failureMessage)
            }
        }
    }

    private fun executeSourceDependencyTask() {
        val invocation1 = invocation ?: throw RuntimeException(
                "Attempting to invoke source dependency task ${name} on ${project} " +
                        "but 'invoke()' was not called to set the command-line"
        )
        println("Invoking ${invocation1.description} on ${project}...")
        val result = project.exec { spec ->
            spec.commandLine = invocation1.cmdLine
            spec.isIgnoreExitValue =
                    (invocation1.exitCodeBehaviour != SourceDependencyInvocationHandler.ExitCodeBehaviour.FAIL_IMMEDIATELY)
        }
        execResult = result
        if (invocation1.exitCodeBehaviour == SourceDependencyInvocationHandler.ExitCodeBehaviour.FAIL_IMMEDIATELY) {
            result.assertNormalExitValue()
        }
        println()
    }

    /*
     * Adds a doLast closure to handle the "fail at end" behaviour.
     */
    private fun initializeInternal() {
        doLast { it: Task ->
            (it as SourceDependencyTask).maybeFailAtEnd()
        }
    }

    /**
     * Initializes the task with an invocation handler, which describes the command to execute and how its exit code
     * should be handled.
     * @param invocation An invocation handler.
     */
    fun initialize(invocation: SourceDependencyInvocationHandler) {
        this.invocation = invocation

        doLast { it ->
            (it as SourceDependencyTask).executeSourceDependencyTask()
        }

        initializeInternal()
    }

    /**
     * Initializes the task with multiple invocation handlers, each describing the command to execute and how its exit
     * code should be handled, along with a task which will execute the command.
     * @param invocationsWithTasks An ordered collection of invocation descriptions and tasks which implement them.
     */
    fun initialize(invocationsWithTasks: LinkedHashMap<SourceDependencyInvocationHandler, Task>) {
        group = "Source Dependencies"
        val cmdLineDescription = invocationsWithTasks.map { it.key.description }.joinToString(", ")
        description = "Invoke ${cmdLineDescription} on '${project.name}' and dependencies."

        for ((_, t) in invocationsWithTasks) {
            dependsOn(t)
        }
        
        initializeInternal()
    }
}

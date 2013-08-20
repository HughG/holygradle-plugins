package holygradle.source_dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.process.internal.ExecException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Unit tests for {@link SourceDependencyTask} and {@link SourceDependencyInvocationHandler}.
 */
@RunWith(Parameterized)
class SourceDependencyTaskTest extends GroovyTestCase {
    /** True if and only if one {@link SourceDependencyTask} instance in each test should return a failure exit code. */
    private boolean testShouldFail

    /**
     * If true, each test will create one task with several dependent tasks, simulating a multi-project build, where
     * each sub-project has a task and the root project task depends on them.  If false, simulates a single-project
     * build, with only one task.
     */
    private boolean addDependentTasks

    /**
     * This method is called by the {@link Parameterized} test runner, and all test cases in this class are run once
     * with each set of parameters passed to the constructor.
     * @return An {@link Iterable<Object[]>} containing parameter arrays for successive test runs.
     */
    @Parameterized.Parameters public static Iterable<Object[]> data() {
        // Try all combinations of the test parameters.
        return [
            [false, true], // testShouldFail
            [false, true] // addDependentTasks
        ].combinations().collect { ArrayList it -> it.toArray() }
    }

    /**
     * Returns a human-readable description of the parameters of this test case instance.
     * @return A human-readable description of the parameters of this test case instance.
     */
    private String getParametersDescription() {
        return "Parameters: should ${testShouldFail ? '' : 'not '}fail; "+
            "${addDependentTasks ? 'with' : 'no'} dependent tasks. "
    }

    /** Prints a description of the parameters before each test run, since JUnit doesn't make it clear otherwise. */
    @Before public void logParameters() {
        System.out.println(parametersDescription)
    }

    /** Constructor */
    SourceDependencyTaskTest(boolean testShouldFail, boolean addDependentTasks) {
        this.testShouldFail = testShouldFail
        this.addDependentTasks = addDependentTasks
    }

    /**
     * Returns the complete stack trace from a {@link Throwable} as a {@link String}, including "Caused by ..."
     * information for nested {@link Throwable}s.
     * @param t The {@link Throwable} whose stack trace should be returned.
     * @return The complete stack trace as a {@link String}.
     */
    protected String getPrintedStackTrace(Throwable t) {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)
        try {
            t.printStackTrace(pw)
        } finally {
            pw.flush()
        }
        return sw.toString()
    }

    /**
     * Similar to {@link GroovyTestCase#shouldFailWithCause(Class, Closure)} but only matches a {@link Throwable} which
     * both is an instance of {@code clazz} and has a message containing {@code expectedMessage}.  (The base class
     * method matches only on type, then allows you to extract the message.)
     *
     * @param failureMessage A phrase to be included in assertion failure messages from within this method.
     * @param clazz The type of {@link Throwable} to match; subclasses also match.
     * @param expectedMessage A string which must occur within the {@link Throwable#getMessage()} for it to match.
     * @param code The closure which should fail.
     * @return The message from the matching {@link Throwable}.
     */
    protected String shouldFailWithCauseContaining(
        String failureMessage,
        Class clazz,
        String expectedMessage,
        Closure code
    ) {
        Throwable originalThrowable = null
        Throwable matchingThrowable = null
        try {
            code()
        } catch (Throwable throwable) {
            originalThrowable = throwable
            for (Throwable t = throwable; t != null; t = t.cause) {
                if (clazz.isInstance(t) &&
                    t.message.contains(expectedMessage)
                ) {
                    matchingThrowable = t
                    break
                }
            }
        }
        assertNotNull("$failureMessage: Closure ${code.toString()} should have failed with an exception", originalThrowable)
        assertNotNull(
            "$failureMessage: Closure ${code.toString()} should have failed " +
            "with an exception or nested cause of type $clazz " +
            "whose message contained '$expectedMessage' " +
            "but instead failed with ${getPrintedStackTrace(originalThrowable)}",
            matchingThrowable
        )
        return matchingThrowable.message
    }

    /**
     * Initializes {@code task} with a command which will fail if and only if {@code fail} is true, and calls the
     * zero-argument method named {@code setFailureModeMethod} on the task to set its failure-handling mode.
     *
     * @param task The {@link SourceDependencyTask} to initialize.
     * @param fail A flag indicating whether the task should fail when executed.
     * @param setFailureModeMethod The name of one of the methods of {@link SourceDependencyInvocationHandler} which
     * sets the way in which the task should handle failure.
     */
    private static void initializeTask(SourceDependencyTask task, boolean fail, String setFailureModeMethod) {
        SourceDependencyInvocationHandler invocationHandler =
            new SourceDependencyInvocationHandler("cmd.exe", "/c", "exit /b ${fail ? 1 : 0}")
        invocationHandler."$setFailureModeMethod"()
        task.initialize(invocationHandler)
    }

    private static void addIdIfSuccessful(SourceDependencyTask task, Collection<Integer> successfulTaskIds, Integer id) {
        if (task.execResult?.exitValue == 0) { successfulTaskIds.add(id) }
    }

    /**
     * Sets up one or multiple tasks in preparation for a test.  If {@link #addDependentTasks} is true, there will be
     * one task with ID 0, which has three dependent tasks with IDs 1 to 3; if false, there will only be the ID 0 task.
     * The IDs of the tasks are "baked in", to their names and to a closure which adds , not available
     * (In real use, the dependent tasks would be from sub-projects, but that doesn't matter for testing this class.)
     *
     * @param project The project to which the task(s) should belong.
     * @param successfulTaskIds An empty collection; successful tasks will add a unique integer to this.
     * @param setFailureModeMethod The name of one of the methods of {@link SourceDependencyInvocationHandler} which
     * sets the way in which the task should handle failure.
     * @return The root task, with ID 0.
     */
    private SourceDependencyTask setUpTasks(
        Project project,
        Collection<Integer> successfulTaskIds,
        String setFailureModeMethod
    ) {
        // If the test should fail then: if we just have a root task, it should fail, otherwise we have one of the
        // dependent tasks fail.
        boolean rootTaskShouldFail = testShouldFail && !addDependentTasks
        SourceDependencyTask task = (SourceDependencyTask) project.task([type: SourceDependencyTask], "sdt0")
        initializeTask(task, rootTaskShouldFail, setFailureModeMethod)
        task.doLast { addIdIfSuccessful(task, successfulTaskIds, 0) }

        if (addDependentTasks) {
            // Set up three sub-project tasks.  Each depends on the previous one, and the middle one may fail.
            Task previousTask = null;
            for (int i = 1 /* NOT 0 */; i < 4; i++) {
                SourceDependencyTask depTask = (SourceDependencyTask) project.task(
                    [type: SourceDependencyTask],
                    "sdt${i}"
                )
                initializeTask(depTask, testShouldFail && (i == 2), setFailureModeMethod)
                // Capture current value of 'i'; if you use the variable 'i', the doLast closure just adds '4'.
                int capturedIndex = i;
                depTask.doLast { addIdIfSuccessful(depTask, successfulTaskIds, capturedIndex) }
                if (previousTask) {
                    depTask.dependsOn(previousTask)
                }
                task.addDependentTask(depTask)
                previousTask = depTask
            }
        }
        return task
    }

    /**
     * Traverses the {@link Task#getDependsOn()} graph depth-first, collecting as-yet-unseen tasks in {@code tasks},
     * children before parent.  Once this method returns, iterating over {@code tasks} will visit all tasks in
     * dependency order, once each.
     *
     * @param tasks The collection of tasks, in the order they were first seen.
     * @param task The current parent task.
     */
    private void linearizeTaskGraph(LinkedHashSet<Task> tasks, Task task) {
        task.dependsOn
            .findAll { it instanceof Task }
            .each { linearizeTaskGraph(tasks, (Task)it) }
        tasks.add(task)
    }


    /**
     * This is a dummy implementation of the Gradle task graph execution process, to save us having to set up a project
     * connection etc.
     *
     * It isn't guaranteed to be correct in general, but it's enough for this test.
     *
     * @param rootTask The root of the graph of tasks to run.
     */
    private void runTasks(Task rootTask) {
        LinkedHashSet<Task> tasks = new LinkedHashSet<Task>()
        linearizeTaskGraph(tasks, rootTask)
        tasks.each { DefaultTask it -> it.execute() }
    }

    /**
     * Tests a {@link SourceDependencyTask} configured with a {@link SourceDependencyInvocationHandler} whose
     * {@link SourceDependencyInvocationHandler#ignoreFailure()} method has been called.
     */
    @Test
    public void testIgnoreFailure() {
        Project project = ProjectBuilder.builder().withName("testProject").build()
        Collection<Integer> successfulTaskIds = []
        SourceDependencyTask task = setUpTasks(project, successfulTaskIds, "ignoreFailure")

        runTasks(task)

        Object[] expected
        if (testShouldFail) {
            if (addDependentTasks) {
                expected = [1, 3, 0]
            } else {
                expected = []
            }
        } else {
            if (addDependentTasks) {
                expected = [1, 2, 3, 0]
            } else {
                expected = [0]
            }
        }
        assertArrayEquals(expected, successfulTaskIds.toArray())
    }

    /**
     * Tests a {@link SourceDependencyTask} configured with a {@link SourceDependencyInvocationHandler} whose
     * {@link SourceDependencyInvocationHandler#failImmediately()} method has been called.
     */
    @Test
    public void testFailImmediately() {
        Project project = ProjectBuilder.builder().withName("testProject").build()
        Collection<Integer> successfulTaskIds = []
        SourceDependencyTask task = setUpTasks(project, successfulTaskIds, "failImmediately")

        if (testShouldFail) {
            shouldFailWithCauseContaining(
                parametersDescription,
                ExecException.class,
                "finished with non-zero exit value"
            ) {
               runTasks(task)
            }
        } else {
            runTasks(task)
        }

        Object[] expected
        if (testShouldFail) {
            if (addDependentTasks) {
                expected = [1]
            } else {
                expected = []
            }
        } else {
            if (addDependentTasks) {
                expected = [1, 2, 3, 0]
            } else {
                expected = [0]
            }
        }
        assertArrayEquals(expected, successfulTaskIds.toArray())
    }

    /**
     * Tests a {@link SourceDependencyTask} configured with a {@link SourceDependencyInvocationHandler} whose
     * {@link SourceDependencyInvocationHandler#failAtEnd()} method has been called.
     */
    @Test
    public void testFailAtEnd() {
        Project project = ProjectBuilder.builder().withName("testProject").build()
        Collection<Integer> successfulTaskIds = []
        SourceDependencyTask task = setUpTasks(project, successfulTaskIds, "failAtEnd")

        if (testShouldFail) {
            shouldFailWithCauseContaining(
                parametersDescription,
                RuntimeException.class,
                "There was a failure while running"
            ) {
                runTasks(task)
            }
        } else {
            runTasks(task)
        }

        Object[] expected
        if (testShouldFail) {
            if (addDependentTasks) {
                // This case is different from testIgnoreFailure: task 2 is omitted because its invocation fails;
                // task 0 is omitted because it's the root task, and the internal "fail at end" check throws an
                // exception before the "doLast" action added by this test gets a chance to add '0' to the collection.
                expected = [1, 3]
            } else {
                expected = []
            }
        } else {
            if (addDependentTasks) {
                expected = [1, 2, 3, 0]
            } else {
                expected = [0]
            }
        }
        assertArrayEquals(expected, successfulTaskIds.toArray())
    }
}

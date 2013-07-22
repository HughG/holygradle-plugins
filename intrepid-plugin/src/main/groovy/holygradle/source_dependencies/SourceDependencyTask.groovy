package holygradle.source_dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class SourceDependencyTask extends DefaultTask {
    public boolean isRootTask = true
    private SourceDependencyInvocationHandler invocation
    private ExecResult failureResult = null

    public ExecResult getFailureResult() {
        return failureResult
    }

    public void addDependentTask(SourceDependencyTask t) {
        dependsOn(t)
        t.isRootTask = false
    }
    
    
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

    public Map<Task, ExecResult> collectFailures() {
        Map<Task, ExecResult> allFailures = [:]
        collectDependentTasks().each { SourceDependencyTask t ->
            if (t.getFailureResult() != null) {
                allFailures[t] = t.getFailureResult()
            }
        }
        return allFailures
    }

    public void maybeFailAtEnd() {
        if (invocation != null &&
            invocation.exitCodeBehaviour == ExitCodeBehaviour.failAtEnd &&
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

    private void initializeInternal() {
        doLast { SourceDependencyTask it ->
            it.maybeFailAtEnd()
        }
    }

    public void initialize(SourceDependencyInvocationHandler invocation) {
        this.invocation = invocation

        doLast { SourceDependencyTask it ->
            println "Invoking ${invocation.getDescription()} on '${project.name}'..."
            ExecResult result = project.exec { ExecSpec spec ->
                spec.commandLine = invocation.cmdLine
                spec.ignoreExitValue = invocation.exitCodeBehaviour != ExitCodeBehaviour.failImmediately
            }
            if (invocation.exitCodeBehaviour == ExitCodeBehaviour.failImmediately) {
                result.assertNormalExitValue()
            } else if (invocation.exitCodeBehaviour == ExitCodeBehaviour.failAtEnd) {
                if (result.getExitValue() != 0) {
                    it.failureResult = result
                }
            }
            println ""
        }
        
        initializeInternal()
    }
    
    public void initialize(Iterable<SourceDependencyInvocationHandler> invocations, Iterable<Task> invocationTasks) {
        group = "Source Dependencies"
        String cmdLineDescription = invocations.collect { it.description }.join(", ")
        description = "Invoke ${cmdLineDescription} on '${project.name}' and dependencies."

        invocationTasks.each { t ->
            dependsOn(t)
        }
        
        initializeInternal()
    }
}
package holygradle.source_dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class SourceDependencyTask extends DefaultTask {
    private boolean isRootTask = true
    private SourceDependencyInvocationHandler invocation
    private ExecResult failureResult = null
    
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
            if (t.failureResult != null) {
                allFailures[t] = t.failureResult
            }
        }
        return allFailures
    }
    
    private void initializeInternal() {
        doLast { SourceDependencyTask it ->
            if (it.invocation != null &&
                it.invocation.exitCodeBehaviour == ExitCodeBehaviour.failAtEnd && it.isRootTask
            ) {
                Map<Task, ExecResult> allFailures = collectFailures()
                String failureMessage = null
                if (allFailures.size() == 1) {
                    failureMessage = "There was a failure while running ${it.invocation.description}."
                } else if (allFailures.size() > 1) {
                    failureMessage = "There were ${allFailures.size()} failures while running ${it.invocation.description}."
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
package holygradle.source_dependencies

import org.gradle.api.DefaultTask
import org.gradle.process.ExecResult

class SourceDependencyTask extends DefaultTask {
    public boolean isRootTask = true
    public SourceDependencyInvocationHandler invocation
    public ExecResult failureResult = null
    
    public void addDependentTask(SourceDependencyTask t) {
        dependsOn(t)
        t.isRootTask = false
    }
    
    
    public def collectDependentTasks() {
        def taskCollection = []
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
    
    public def collectFailures() {
        def allFailures = [:]
        collectDependentTasks().each { t ->
            if (t.failureResult != null) {
                allFailures[t] = t.failureResult
            }
        }
        return allFailures
    }
    
    private void initializeInternal() {
        doLast {
            if (invocation != null && invocation.exitCodeBehaviour == ExitCodeBehaviour.failAtEnd && isRootTask) {
                def allFailures = collectFailures()
                String failureMessage = null
                if (allFailures.size() == 1) {
                    failureMessage = "There was a failure while running ${invocation.getDescription()}."
                } else if (allFailures.size() > 1) {
                    failureMessage = "There were ${allFailures.size()} failures while running ${invocation.getDescription()}."
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

        doLast {
            println "Invoking ${invocation.getDescription()} on '${project.name}'..."
            ExecResult result = project.exec {
                commandLine = invocation.cmdLine
                ignoreExitValue = invocation.exitCodeBehaviour != ExitCodeBehaviour.failImmediately
            }
            if (invocation.exitCodeBehaviour == ExitCodeBehaviour.failImmediately) {
                result.assertNormalExitValue()
            } else if (invocation.exitCodeBehaviour == ExitCodeBehaviour.failAtEnd) {
                if (result.getExitValue() != 0) {
                    failureResult = result
                }
            }
            println ""
        }
        
        initializeInternal()
    }
    
    public void initialize(def invocations, def invocationTasks) {
        group = "Source Dependencies"
        def cmdLineDescription = invocations.collect { it.getDescription() }.join(", ")
        description = "Invoke ${cmdLineDescription} on '${project.name}' and dependencies."

        invocationTasks.each { t ->
            dependsOn(t)
        }
        
        initializeInternal()
    }
}
package holygradle.source_dependencies

import org.gradle.api.Project
import org.gradle.api.Task

class SourceDependencyTaskHandler {
    public final String name
    public def invocations = []
    
    public static Collection<SourceDependencyTaskHandler> createContainer(Project project) {
        project.extensions.sourceDependencyTasks = project.container(SourceDependencyTaskHandler)
        project.extensions.sourceDependencyTasks
    }
    
    public SourceDependencyTaskHandler(String name) {
        this.name = name
    }
    
    public SourceDependencyInvocationHandler invoke(String... params) {
        def cmdLine = []
        params.each { cmdLine.add(it) }
        def invocationHandler = new SourceDependencyInvocationHandler(cmdLine)
        invocations.add(invocationHandler)
        invocationHandler
    }
    
    public Task defineTask(Project project) {
        def commandTask = project.tasks.findByPath(name)
        
        // Check that we haven't defined this task already.
        if (commandTask == null) {
            if (invocations.size() == 1) {
                commandTask = project.task(name, type: SourceDependencyTask) {
                    initialize(invocations[0])
                }
            } else {
                def invocationTasks = []
                invocations.eachWithIndex { invocation, index ->
                    def t = project.task("${name}_${index}", type: SourceDependencyTask) {
                        initialize(invocation)
                    }
                    invocationTasks.add(t)
                }
                commandTask = project.task(name, type: SourceDependencyTask) {
                    initialize(invocations, invocationTasks)
                }
            }
            commandTask.group = "Source Dependencies"
            def cmdLineDescription = invocations.collect { "'" + it.cmdLine.join(" ") + "'" }.join(", ")
            commandTask.description = "Invoke ${cmdLineDescription} on '${project.name}' and dependencies."
        }
        
        commandTask
    }
    
    public void configureTaskDependencies(Project project) {
        def commandTask = defineTask(project)
        def taskDependencies = project.extensions.findByName("taskDependencies")
        
        // Add a dependency from the top-level task to tasks of the same name belonging to
        // source-dependency projects.
        if (taskDependencies != null) {
            taskDependencies.get(commandTask.name).each { dep ->
                commandTask.addDependentTask(dep)
            }
        }
        
        if (invocations.size() > 1) {
            // For each individual invocation task, add dependencies to tasks of the same name
            // belonging to source-dependency projects.
            if (taskDependencies != null) {
                for (int i = 0; i < invocations.size(); i++) {
                    def thisTask = project.tasks.getByName("${name}_${i}")
                    taskDependencies.get(thisTask.name).each { dep ->
                        thisTask.addDependentTask dep
                    }
                }
            }
            
            // Each individual invocation task (beyond the first) should depend upon *all* previous
            // invocation tasks belonging to all projects. ???
            for (int i = 1; i < invocations.size(); i++) {
                def thisTask = project.tasks.getByName("${name}_${i}")
                def prevTasks = project.rootProject.getTasksByName("${name}_${i-1}", true)
                prevTasks.each { t ->
                    thisTask.dependsOn t
                }
            }
        }
    }
}

package holygradle.source_dependencies

import holygradle.custom_gradle.TaskDependenciesExtension
import org.gradle.api.Project
import org.gradle.api.Task

class SourceDependencyTaskHandler {
    public final String name
    public Collection<SourceDependencyInvocationHandler> invocations = []
    
    public static Collection<SourceDependencyTaskHandler> createContainer(Project project) {
        project.extensions.sourceDependencyTasks = project.container(SourceDependencyTaskHandler)
        project.extensions.sourceDependencyTasks
    }
    
    public SourceDependencyTaskHandler(String name) {
        this.name = name
    }
    
    public SourceDependencyInvocationHandler invoke(String... params) {
        SourceDependencyInvocationHandler invocationHandler = new SourceDependencyInvocationHandler(params.clone())
        invocations.add(invocationHandler)
        invocationHandler
    }
    
    public SourceDependencyTask defineTask(Project project) {
        SourceDependencyTask commandTask = project.tasks.findByPath(name) as SourceDependencyTask
        
        // Check that we haven't defined this task already.
        if (commandTask == null) {
            if (invocations.size() == 1) {
                commandTask = (SourceDependencyTask)project.task(
                    name,
                    type: SourceDependencyTask
                ) { SourceDependencyTask it ->
                    it.initialize(invocations.find()) // finds the first task
                }
            } else {
                LinkedHashMap<SourceDependencyInvocationHandler, Task> invocationsWithTasks = [:]
                invocations.eachWithIndex { invocation, index ->
                    Task t = project.task("${name}_${index}", type: SourceDependencyTask) { SourceDependencyTask it ->
                        it.initialize(invocation)
                    }
                    invocationsWithTasks[invocation] = t
                }
                commandTask = (SourceDependencyTask)project.task(
                    name,
                    type: SourceDependencyTask
                ) { SourceDependencyTask it ->
                    it.initialize(invocationsWithTasks)
                }
            }
            commandTask.group = "Source Dependencies"
            String cmdLineDescription = invocations.collect { "'" + it.cmdLine.join(" ") + "'" }.join(", ")
            commandTask.description = "Invoke ${cmdLineDescription} on '${project.name}' and dependencies."
        }
        
        commandTask
    }
    
    public void configureTaskDependencies(Project project) {
        SourceDependencyTask commandTask = defineTask(project)
        TaskDependenciesExtension taskDependencies =
            project.extensions.findByName("taskDependencies") as TaskDependenciesExtension
        
        // Add a dependency from the top-level task to tasks of the same name belonging to
        // source-dependency projects.
        if (taskDependencies != null) {
            taskDependencies.get(commandTask.name).each { SourceDependencyTask dep ->
                commandTask.addDependentTask(dep)
            }
        }
        
        if (invocations.size() > 1) {
            // For each individual invocation task, add dependencies to tasks of the same name
            // belonging to source-dependency projects.
            if (taskDependencies != null) {
                for (int i = 0; i < invocations.size(); i++) {
                    SourceDependencyTask thisTask = project.tasks.getByName("${name}_${i}") as SourceDependencyTask
                    taskDependencies.get(thisTask.name).each { SourceDependencyTask dep ->
                        thisTask.addDependentTask dep
                    }
                }
            }
            
            // Each individual invocation task (beyond the first) should depend upon *all* previous
            // invocation tasks belonging to all projects. ???
            for (int i = 1; i < invocations.size(); i++) {
                Task thisTask = project.tasks.getByName("${name}_${i}")
                Set<Task> prevTasks = project.rootProject.getTasksByName("${name}_${i-1}", true)
                prevTasks.each { Task t ->
                    thisTask.dependsOn t
                }
            }
        }
    }
}

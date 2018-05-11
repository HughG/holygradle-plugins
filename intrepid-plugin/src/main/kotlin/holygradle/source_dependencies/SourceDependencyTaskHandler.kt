package holygradle.source_dependencies


import org.gradle.api.Project
import org.gradle.api.Task
import holygradle.kotlin.dsl.task

class SourceDependencyTaskHandler(val name: String) {
    companion object {
        @JvmStatic
        fun createContainer(project: Project): Collection<SourceDependencyTaskHandler> {
            val container = project.container(SourceDependencyTaskHandler::class.java)
            project.extensions.add("sourceDependencyTasks", container)
            return container
        }
    }
    private val invocations = mutableListOf<SourceDependencyInvocationHandler>()
    
    fun invoke(vararg params: String): SourceDependencyInvocationHandler {
        val invocationHandler = SourceDependencyInvocationHandler(*params)
        invocations.add(invocationHandler)
        return invocationHandler
    }
    
    fun defineTask(project: Project): SourceDependencyTask {
        var commandTask = project.tasks.findByPath(name) as? SourceDependencyTask
        
        // Check that we haven't defined this task already.
        if (commandTask == null) {
            if (invocations.size == 1) {
                commandTask = project.task<SourceDependencyTask>(name) {
                    this.initialize(invocations.first()) // finds the first task
                }
            } else {
                val invocationsWithTasks = linkedMapOf<SourceDependencyInvocationHandler, Task>()
                invocations.forEachIndexed { index, invocation ->
                    val t = project.task<SourceDependencyTask>("${name}_${index}") { initialize(invocation) }
                    invocationsWithTasks[invocation] = t
                }
                commandTask = project.task<SourceDependencyTask>(name) {
                    initialize(invocationsWithTasks)
                }
            }
            commandTask.group = "Source Dependencies"
            val cmdLineDescription = invocations.map {
                it.cmdLine.joinToString(prefix = "'", separator = " ", postfix = "'")
            }.joinToString(", ")
            commandTask.description = "Invoke ${cmdLineDescription} on '${project.name}' and dependencies."

            configureTaskDependencies(commandTask)
        }
        
        return commandTask
    }

    private fun configureTaskDependencies(commandTask: SourceDependencyTask) {
        val project = commandTask.project
        val sourceDependenciesState =
            project.extensions.findByName("sourceDependenciesState") as? SourceDependenciesStateHandler

        val allConfigurationsPublishingSourceDependencies = sourceDependenciesState?.getAllConfigurationsPublishingSourceDependencies()
        if (allConfigurationsPublishingSourceDependencies != null) {
            // Add a dependency from the top-level task to tasks of the same name belonging to
            // source-dependency projects.
            for (conf in allConfigurationsPublishingSourceDependencies) {
                commandTask.dependsOn(conf.getTaskDependencyFromProjectDependency(true, commandTask.name))
            }
        }

        if (invocations.size > 1) {
            // For each individual invocation task, add dependencies to tasks of the same name
            // belonging to source-dependency projects.
            if (allConfigurationsPublishingSourceDependencies != null) {
                for (i in 0 until invocations.size) {
                    val thisTask = project.tasks.getByName("${name}_${i}") as SourceDependencyTask
                    for (conf in allConfigurationsPublishingSourceDependencies) {
                        thisTask.dependsOn(conf.getTaskDependencyFromProjectDependency(true, thisTask.name))
                    }
                }
            }

            // Each individual invocation task (beyond the first) should depend upon *all* previous
            // invocation tasks belonging to all projects.
            for (i in 0 until invocations.size) {
                val thisTask = project.tasks.getByName("${name}_${i}")
                val prevTasks = project.rootProject.getTasksByName("${name}_${i-1}", true)
                for (it in prevTasks) {
                    thisTask.dependsOn(it)
                }
            }
        }
    }
}

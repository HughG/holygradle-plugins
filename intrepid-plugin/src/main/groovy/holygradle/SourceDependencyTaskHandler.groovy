package holygradle

import org.gradle.api.*
import org.gradle.api.tasks.*

class SourceDependencyTaskHandler {
    public final String name
    public def cmdLines = []
    
    public static def createContainer(Project project) {
        project.extensions.sourceDependencyTasks = project.container(SourceDependencyTaskHandler)  { taskName ->
            def sourceDep = project.sourceDependencies.extensions.create(taskName, SourceDependencyTaskHandler, taskName)  
        }
        project.extensions.sourceDependencyTasks
    }
    
    public SourceDependencyTaskHandler(String name) {
        this.name = name
    }
    
    public void invoke(String... params) {
        def cmdLine = []
        params.each { cmdLine.add(it) }
        cmdLines.add(cmdLine)
    }
    
    public void defineTask(Project project) {
        def commandTask = project.tasks.findByPath(name)
        
        if (commandTask == null) {
            commandTask = project.task(name, type: DefaultTask) {
                group = "Source Dependencies"
                def cmdLineDescription = cmdLines.collect { "'" + it.join(" ") + "'" }.join(", ")
                description = "Invoke ${cmdLineDescription} on '${project.name}' and dependencies."

                doLast {
                    cmdLines.each { cmdLine ->
                        println "Invoking ${cmdLine} on '${project.name}'..."
                        project.exec {
                            commandLine = cmdLine
                            ignoreExitValue = true
                        }
                        println ""
                    }
                }
            }
        }
        
        def taskDependencies = project.extensions.findByName("taskDependencies")
        if (taskDependencies != null) {
            commandTask.dependsOn taskDependencies.get(commandTask.name)
        }
    }
}

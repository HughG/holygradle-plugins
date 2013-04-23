package holygradle.custom_gradle

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*
import org.gradle.api.tasks.wrapper.*
import org.gradle.api.logging.*

class TaskDependenciesExtension {
    private Project project
   
    TaskDependenciesExtension(Project project) {
        this.project = project
    }

    // Returns all tasks with the same name, in projects for which dependencies have been added as BuildDependency info.
    // It returns a Set<Object> because that's the type accepted by Task.dependsOn().
    public Set<Object> get(String taskName) {
        Set<Object> buildDeps = new HashSet<Object>()
        def rootProj = project.rootProject
        def buildDependencies = project.extensions.findByName("buildDependencies")
        buildDependencies.each { BuildDependency buildDep ->
            Project depProj = buildDep.getProject(rootProj)
            if (depProj != null) {
                def depTask = depProj.tasks.findByName(taskName)
                if (depTask != null) {
                    buildDeps.add(depTask)
                }
            }
        }
        buildDeps
    }
    
    public void configure(String taskName) {
        configure(project.tasks.getByName(taskName))
    }
    
    public void configure(Task t) {
        project.gradle.projectsEvaluated {
            configureNow(t)
        }
    }
    
    public void configureNow(String taskName) {
        configureNow(project.tasks.getByName(taskName))
    }
    
    public void configureNow(Task t) {
        def deps = get(t.name)
        //println "task ${project.name}.${t.name} depends on : $deps"
        t.dependsOn deps
    }
}
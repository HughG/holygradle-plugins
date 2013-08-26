package holygradle.unpacking

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Test dummy class which looks like {@link holygradle.buildscript.BuildScriptDependencies}.
 */
class DummyBuildScriptDependencies {
    private final Project project
   
    public DummyBuildScriptDependencies(Project project) {
        this.project = project
    }
    
    public Task getUnpackTask(String dependencyName) {
        Task task = project.task(dependencyName, type: DefaultTask)
        task.ext.destinationDir = getPath(dependencyName)
        task
    }
    
    public File getPath(String dependencyName) {
        new File("path/to/${dependencyName}")
    }
}

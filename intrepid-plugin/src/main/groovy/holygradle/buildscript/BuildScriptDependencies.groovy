package holygradle.buildscript

import org.gradle.api.Project
import org.gradle.api.Task

class BuildScriptDependencies {
    private final Project project
    private Map<String, BuildScriptDependency> dependencies = [:]
    
    public static BuildScriptDependencies initialize(Project project) {
        BuildScriptDependencies deps
        if (project == project.rootProject) {
            deps = new BuildScriptDependencies(project)
        } else {
            deps = project.rootProject.extensions.findByName("buildScriptDependencies") as BuildScriptDependencies
        }
        project.extensions.add("buildScriptDependencies", deps)
        deps
    }
    
    public BuildScriptDependencies(Project project) {
        this.project = project
    }
    
    public void add(String dependencyName, boolean unpack = false, boolean optional = false) {
        dependencies[dependencyName] = new BuildScriptDependency(project, dependencyName, unpack, optional)
    }
    
    public Task getUnpackTask(String dependencyName) {
        dependencies[dependencyName].getUnpackTask()
    }

    public File getPath(String dependencyName) {
        dependencies[dependencyName].getPath()
    }
}

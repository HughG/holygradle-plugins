package holygradle

import org.gradle.*
import org.gradle.api.*

class BuildScriptDependencies {
    private final Project project
    private def dependencies = [:]
    
    public static BuildScriptDependencies initialize(Project project) {
        BuildScriptDependencies deps = new BuildScriptDependencies(project)
        project.ext.buildScriptDependencies = deps
        deps
    }
    
    public BuildScriptDependencies(Project project) {
        this.project = project
    }
    
    public void add(String dependencyName, boolean needsUnpacked) {
        dependencies[dependencyName] = new BuildScriptDependency(project, dependencyName, needsUnpacked)
    }
    
    public Task getUnpackTask(String dependencyName) {
        dependencies[dependencyName].getUnpackTask()
    }
    
    public Task getRootUnpackTask(String dependencyName) {
        if (project == project.rootProject) {
            return getUnpackTask(dependencyName)
        } else {
            return project.rootProject.ext.buildScriptDependencies.getUnpackTask(dependencyName)
        }
    }
    
    public String getUnpackTaskName(String dependencyName) {
        Helper.MakeCamelCase("extract", dependencyName)
    }
    
    public String getPath(String dependencyName) {
        dependencies[dependencyName].getPath()
    }
}

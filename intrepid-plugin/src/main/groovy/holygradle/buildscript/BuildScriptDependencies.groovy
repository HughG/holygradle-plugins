package holygradle.buildscript

import holygradle.Helper
import org.gradle.api.*

class BuildScriptDependencies {
    private final Project project
    private def dependencies = [:]
    
    public static BuildScriptDependencies initialize(Project project) {
        BuildScriptDependencies deps
        if (project == project.rootProject) {
            deps = new BuildScriptDependencies(project)
        } else {
            deps = project.rootProject.extensions.findByName("buildScriptDependencies")
        }
        project.extensions.add("buildScriptDependencies", deps)
        deps
    }
    
    public BuildScriptDependencies(Project project) {
        this.project = project
    }
    
    public void add(String dependencyName, boolean unpack=false) {
        dependencies[dependencyName] = new BuildScriptDependency(project, dependencyName, unpack)
    }
    
    public Task getUnpackTask(String dependencyName) {
        dependencies[dependencyName].getUnpackTask()
    }
    
    public String getUnpackTaskName(String dependencyName) {
        // Unfortunately can't use the CamelCase helper from custom-gradle-core because we're
        // executing this from the buildscript block before the custom-gradle-core-plugin has
        // been added to the classpath.
        Helper.MakeCamelCase("extract", dependencyName)
    }
    
    public File getPath(String dependencyName) {
        dependencies[dependencyName].getPath()
    }
}

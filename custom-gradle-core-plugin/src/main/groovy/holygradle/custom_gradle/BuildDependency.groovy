package holygradle.custom_gradle

import org.gradle.api.*

class BuildDependency {
    private final String name
    
    BuildDependency(String dep_name) {
        this.name = dep_name
    }
    
    public Project getProject(Project anyProject) {
        // Make sure we're really using the root project.
        Project depProj = null
        for (p in anyProject.rootProject.allprojects) {
            if (p.name == name) {
                depProj = p
            }
        }
        depProj
    }
    
}
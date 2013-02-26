package holygradle

import org.gradle.api.*
import java.nio.file.Paths

class DependencyHandler {
    public final String name
    private final Project project
    
    public DependencyHandler(String depName) {
        this.name = depName
    }
    
    public DependencyHandler(String depName, Project project) {
        this.name = depName
        this.project = project
    }
    
    public String getFullTargetPath() {
        name
    }
    
    public String getFullTargetPathRelativeToRootProject() {
        Helper.relativizePath(new File(project.projectDir, name), project.rootProject.projectDir)
    }
        
    public File getAbsolutePath() {
        new File(project.projectDir, name)
    }
    
    public String getTargetName() {
        def pathMatch = name =~ /(.*?)([ \w_\-\<\>]+)$/
        pathMatch[0][2]
    }
}
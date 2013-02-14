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
    
    public String getRelativePath() {
        def pathMatch = name =~ /(.*?)([ \w_\-\<\>]+)$/
        def relativePath = pathMatch[0][1]
        if (relativePath == "." || relativePath == "./" || relativePath == ".\\") {
            relativePath = ""
        }
        relativePath
    }
    
    public File getAbolutePath() {
        new File(project.projectDir, name)
    }
    
    public File getRelativePath(Project project) { 
        if (project != null) {
            new File(project.projectDir, getRelativePath())
        } else {
            new File(getRelativePath())
        }
    }
    
    public String getTargetName() {
        def pathMatch = name =~ /(.*?)([ \w_\-\<\>]+)$/
        pathMatch[0][2]
    }
}
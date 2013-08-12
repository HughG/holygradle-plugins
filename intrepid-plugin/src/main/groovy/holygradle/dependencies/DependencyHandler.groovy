package holygradle.dependencies

import holygradle.Helper
import org.gradle.api.Project
import java.util.regex.Matcher

abstract class DependencyHandler {
    public final String name
    public final String targetName
    private final Project project
    
    public DependencyHandler(String depName) {
        this.name = depName
        this.targetName = (new File(depName)).name
    }
    
    public DependencyHandler(String depName, Project project) {
        this(depName)
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
}
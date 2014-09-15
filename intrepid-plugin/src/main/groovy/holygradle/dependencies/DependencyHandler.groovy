package holygradle.dependencies

import holygradle.Helper
import org.gradle.api.Project
import java.util.regex.Matcher

abstract class DependencyHandler {
    /**
     * The path to the folder in which the dependency is to appear, relative to the containing project, as given by
     * {@link #project}.
     */
    public final String name
    /**
     * The name of the folder in which the dependency is to appear; that is, the past part of {@link #name}.
     */
    public final String targetName
    /**
     * The {@Link Project} containing this dependency.
     */
    public final Project project
    
    public DependencyHandler(String depName) {
        this(depName, null)
    }
    
    public DependencyHandler(String depName, Project project) {
        this.name = depName
        this.targetName = (new File(depName)).name
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
package holygradle.artifacts

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.util.Configurable

class ProjectConfigurationSet extends DefaultConfigurationSet implements Configurable<ProjectConfigurationSet> {
    private final Project project

    public ProjectConfigurationSet(String name, Project project) {
        super(name)
        this.project = project
    }

    @Override
    ProjectConfigurationSet configure(Closure closure) {
        closure.delegate = this
        closure(this)
        // Force configurations to be created
        this.getConfigurations(project)
        return this
    }

    public Map<Map<String, String>, Configuration> getConfigurations() {
        return getConfigurations(project)
    }
}

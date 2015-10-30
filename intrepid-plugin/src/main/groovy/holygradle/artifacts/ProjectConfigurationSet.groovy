package holygradle.artifacts

import org.gradle.api.Project
import org.gradle.util.Configurable

class ProjectConfigurationSet extends DefaultConfigurationSet implements Configurable<ProjectConfigurationSet> {
    private final Project project
    private static final DefaultVisualStudioConfigurationSetTypes handyTypes = new DefaultVisualStudioConfigurationSetTypes()

    ProjectConfigurationSet(String name, Project project) {
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
}

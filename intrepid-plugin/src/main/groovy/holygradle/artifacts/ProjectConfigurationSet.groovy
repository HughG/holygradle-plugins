package holygradle.artifacts

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.util.Configurable
import org.gradle.util.ConfigureUtil

/**
 * This class represents a configuration set which is connected to a specific @{link Project}.
 */
class ProjectConfigurationSet extends DefaultConfigurationSet implements Configurable<ProjectConfigurationSet> {
    private final Project project

    public ProjectConfigurationSet(String name, Project project) {
        super(name)
        this.project = project
    }

    @Override
    public ProjectConfigurationSet configure(Closure closure) {
        ConfigureUtil.configureSelf(closure, this);
        // Force configurations to be created
        this.getConfigurationsMap(project)
        return this
    }

    public Map<Map<String, String>, Configuration> getConfigurationsMap() {
        return getConfigurationsMap(project)
    }

    public List<Configuration> getConfigurations() {
        return new ArrayList<Configuration>(getConfigurationsMap(project).values())
    }
}

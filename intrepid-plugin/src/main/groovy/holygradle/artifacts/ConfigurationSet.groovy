package holygradle.artifacts

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

public interface ConfigurationSet extends Named {
    ConfigurationSetType getType()
    void setType(ConfigurationSetType type)
    void type(ConfigurationSetType type)

    Map<Map<String, String>, String> getConfigurationNames()

    Map<Map<String, String>, Configuration> getConfigurations(Project project)
}
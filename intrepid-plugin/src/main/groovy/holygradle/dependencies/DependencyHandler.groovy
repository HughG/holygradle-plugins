package holygradle.dependencies

import holygradle.Helper
import holygradle.artifacts.ConfigurationSet
import holygradle.artifacts.ConfigurationSetType
import org.gradle.api.Project

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

    private final Collection<AbstractMap.SimpleEntry<String, String>> configurations = []

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

    public Collection<AbstractMap.SimpleEntry<String, String>> getConfigurations() {
        configurations
    }

    public String getFullTargetPathRelativeToRootProject() {
        Helper.relativizePath(new File(project.projectDir, name), project.rootProject.projectDir)
    }
        
    public File getAbsolutePath() {
        new File(project.projectDir, name)
    }

    public abstract void configuration(String config)

    public void configuration(String... configs) {
        configs.each { String config ->
            configuration(config)
        }
    }

    public void configurationSet(Map attrs, ConfigurationSet source, ConfigurationSet target) {
        Collection<String> mappings = source.type.getMappingsTo(attrs, source, target)
        // IntelliJ doesn't understand the spread operator very well.
        //noinspection GroovyAssignabilityCheck
        configuration(*mappings)
    }

    public void configurationSet(Map attrs, ConfigurationSet source, ConfigurationSetType targetType) {
        Collection<String> mappings = source.type.getMappingsTo(attrs, source, targetType)
        // IntelliJ doesn't understand the spread operator very well.
        //noinspection GroovyAssignabilityCheck
        configuration(*mappings)
    }

    public void configurationSet(ConfigurationSet source, ConfigurationSet target) {
        configurationSet([:], source, target)
    }

    public void configurationSet(ConfigurationSet source, ConfigurationSetType targetType) {
        configurationSet([:], source, targetType)
    }

    public void configurationSet(String source, ConfigurationSet target) {
        Collection<String> mappings = target.type.getMappingsFrom(source, target)
        // IntelliJ doesn't understand the spread operator very well.
        //noinspection GroovyAssignabilityCheck
        configuration(*mappings)
    }

    public void configurationSet(String source, ConfigurationSetType targetType) {
        Collection<String> mappings = targetType.getMappingsFrom(source)
        // IntelliJ doesn't understand the spread operator very well.
        //noinspection GroovyAssignabilityCheck
        configuration(*mappings)
    }
}
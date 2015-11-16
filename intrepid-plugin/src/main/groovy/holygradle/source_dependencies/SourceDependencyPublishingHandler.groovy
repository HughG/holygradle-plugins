package holygradle.source_dependencies

import holygradle.Helper
import holygradle.artifacts.ConfigurationSet
import holygradle.artifacts.ConfigurationSetType
import org.gradle.api.Project

class SourceDependencyPublishingHandler {
    private final String dependencyName
    private final Project fromProject
    private Collection<String> originalConfigurations = []
    private Collection<AbstractMap.SimpleEntry<String, String>> configurations = []
    private String publishVersion = null
    private String publishGroup = null
    
    public SourceDependencyPublishingHandler(String dependencyName, Project fromProject) {
        this.dependencyName = dependencyName
        this.fromProject = fromProject
        configuration("everything")
    }
    
    public void configuration(String config) {
        originalConfigurations.add(config)
        Collection<AbstractMap.SimpleEntry<String, String>> newConfigs = []
        Helper.parseConfigurationMapping(config, newConfigs, "Formatting error for '$dependencyName' in 'sourceDependencies'.")
        configurations.addAll(newConfigs)
        
        // Add project dependencies between "sourceDependency"s, but only if they exist (e.g. after fAD).  This allows
        // Gradle's dependency resolution system to spot any conflicts between any _other_ dependencies in the
        // "fromProject" and the project for "dependencyName", such as packed dependencies.  See GR #2037.
        //
        // TODO 2013-05-15 HughG: I think ...
        //    - This belongs in SourceDependencyHandler, not in a "publishing" sub-object, because dependencies apply
        //      at build time, not only at publishing time.  The lack of build-time dependencies caused GR #2037.
        //    - This object shouldn't let you set the version, because at build time you just depend on the source which
        //      is there (which has no version) and at publishing time, you depend on whatever version gets published.
        //      It makes no sense to use one version of the source, but depend on a hard-coded published version.
        //    - This object shouldn't allow the group to be configured, because it can be inferred from the source
        //      dependency's project.  I don't think it's ever used, anyway.
        //    - Therefore, in fact, this class needn't exist at all.
        //
        // ... but I don't want to refactor anything until I have unit tests which check the effect of this method (i.e.,
        // that the correct ivy.xml file is produced).
        Project rootProject = this.fromProject.rootProject
        Project depProject = rootProject.findProject(":${this.dependencyName}")
        if (depProject == null) {
            if (!newConfigs.empty) {
                this.fromProject.logger.info(
                    "Not creating project dependencies from ${fromProject.name} on ${this.dependencyName}, " +
                    "because ${this.dependencyName} has no project file."
                )
            }
        } else if (!depProject.projectDir.exists()) {
            if (!newConfigs.empty) {
                this.fromProject.logger.info(
                    "Not creating project dependencies from ${fromProject.name} on ${this.dependencyName}, " +
                    "because ${this.dependencyName} has yet to be fetched."
                )
            }
        } else {
            for (conf in newConfigs) {
                String fromConf = conf.key
                String toConf = conf.value
                this.fromProject.logger.info "sourceDependencies: adding project dependency " +
                    "from ${fromProject.group}:${fromProject.name} conf=${fromConf} " +
                    "on :${this.dependencyName} conf=${toConf}"
                this.fromProject.dependencies.add(
                    fromConf,
                    rootProject.dependencies.project(path: ":${this.dependencyName}", configuration: toConf)
                )
            }
        }
    }
    
    public void configuration(String... configs) {
        configs.each { String config ->
            configuration(config)
        }
    }

    public void mapConfigurationSet(Map attrs, ConfigurationSet source, ConfigurationSet target) {
        Collection<String> mappings = source.type.getMappingsTo(attrs, source, target)
        // IntelliJ doesn't understand the spread operator very well.
        //noinspection GroovyAssignabilityCheck
        configuration(*mappings)
    }

    public void mapConfigurationSet(Map attrs, ConfigurationSet source, ConfigurationSetType targetType) {
        Collection<String> mappings = source.type.getMappingsTo(attrs, source, targetType)
        // IntelliJ doesn't understand the spread operator very well.
        //noinspection GroovyAssignabilityCheck
        configuration(*mappings)
    }

    public void mapConfigurationSet(ConfigurationSet source, ConfigurationSet target) {
        mapConfigurationSet([:], source, target)
    }

    public void mapConfigurationSet(ConfigurationSet source, ConfigurationSetType targetType) {
        mapConfigurationSet([:], source, targetType)
    }

    public void mapConfigurationSet(String source, ConfigurationSet target) {
        Collection<String> mappings = target.type.getMappingsFrom(source, target)
        // IntelliJ doesn't understand the spread operator very well.
        //noinspection GroovyAssignabilityCheck
        configuration(*mappings)
    }

    public void mapConfigurationSet(String source, ConfigurationSetType targetType) {
        Collection<String> mappings = targetType.getMappingsFrom(source)
        // IntelliJ doesn't understand the spread operator very well.
        //noinspection GroovyAssignabilityCheck
        configuration(*mappings)
    }

    public void version(String ver) {
        publishVersion = ver
    }

    public void group(String g) {
        publishGroup = g
    }

    public String getPublishVersion() {
        publishVersion
    }

    public String getPublishGroup() {
        publishGroup
    }

    public Collection<AbstractMap.SimpleEntry<String, String>> getConfigurations() {
        return configurations
    }
}

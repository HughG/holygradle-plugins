package holygradle.source_dependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * This project extension provides information about the state of source dependencies.
 */
class SourceDependenciesStateHandler {
    private final Project project

    public static SourceDependenciesStateHandler createExtension(Project project) {
        project.extensions.sourceDependenciesState = new SourceDependenciesStateHandler(project)
        project.sourceDependenciesState
    }

    /**
     * Creates an instance of {@link SoureDependenciesStateHandler} for the given project.
     *
     * @param project The project to which this extension instance applies.
     */
    public SourceDependenciesStateHandler(Project project) {
        this.project = project
    }

    /**
     * Returns a list of all configurations in the given {@code project} which have a dependency on a configuration
     * of a source dependency in another project.  It only makes sense to call this method once all projects are
     * evaluated.  Calling
     * {@link org.gradle.api.artifacts.Configuration#getTaskDependencyFromProjectDependency(boolean, java.lang.String)}
     * on the resulting configurations allow you to set up cross-project task dependencies need to take account of
     * source dependencies.
     * @return A list of configurations which depend on source dependencies.
     */
    public Collection<Configuration> getAllConfigurationsPublishingSourceDependencies() {
        // Get a flattened list of all configurations in this project which have dependencies on configurations in
        // source dependencies (which are used when this project and its source dependencies are published together).
        Collection<String> configurationNames =
            project.sourceDependencies.collectMany { SourceDependencyHandler sourceDependency ->
                // Find all the source dependencies we actually publish.  Gradle cross-project dependencies are in
                // terms of configurations of those projects, so we find all the configurations of the given project
                // which depend on some configuration of a published source dependency project.  The keys in the
                // sourceDependency.publishing.configurations collection are the names of configurations in the given
                // project.
                sourceDependency.publishing.configurations.collect(new HashSet<String>()) { it.key }
            }
        // Get a unique set of actual configuration objects.
        configurationNames = configurationNames.unique()
        return configurationNames.collect { project.configurations.getByName(it) }
    }
}

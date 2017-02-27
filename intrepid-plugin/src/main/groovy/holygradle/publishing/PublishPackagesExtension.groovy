package holygradle.publishing

import holygradle.unpacking.PackedDependenciesStateSource
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.Publication

public interface PublishPackagesExtension {
    /**
     * Since Gradle 1.5 the ivy-publish plugin doesn't let you modify publications, related tasks, etc. once the PublishingExtension is
     * accessed through the "project.publishing" property. Instead you should do all configuration in "project.publishing {}" blocks.
     * Therefore we can't provide access to the "project.publishing.repositories" collection.  This method exists only to throw an
     * exception which will tell people how to convert from the property-access style used in older versions of (Holy) Gradle.
     * @return Does not return: always throws {@link UnsupportedOperationException}.
     */
    RepositoryHandler getRepositories()

    void repositories(Action<RepositoryHandler> configure)

    boolean getCreateDefaultPublication()
    void setCreateDefaultPublication(boolean create)

    boolean getPublishPrivateConfigurations()
    void setPublishPrivateConfigurations(boolean publish)

    void defaultPublication(Action<Publication> configure)

    void republish(Closure closure)

    RepublishHandler getRepublishHandler()

    void defineCheckTask(PackedDependenciesStateSource packedDependenciesStateSource)
}
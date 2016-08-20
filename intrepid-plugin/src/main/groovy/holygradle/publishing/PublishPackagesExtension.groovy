package holygradle.publishing

import holygradle.unpacking.PackedDependenciesStateSource
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.Publication

public interface PublishPackagesExtension {
    void group(String publishGroup)
    
    void name(String publishName)
    
    void nextVersionNumber(String versionNo)
    
    void nextVersionNumber(Closure versionNumClosure)
    
    void nextVersionNumberAutoIncrementFile(String versionNumberFilePath)
    
    void nextVersionNumberEnvironmentVariable(String versionNumberEnvVar)

    void repositories(Action<RepositoryHandler> configure)

    boolean getCreateDefaultPublication()
    void setCreateDefaultPublication(boolean create)

    boolean getPublishPrivateConfigurations()
    void setPublishPrivateConfigurations(boolean publish)

    Publication getDefaultPublication()

    void republish(Closure closure)

    RepublishHandler getRepublishHandler()

    void defineCheckTask(PackedDependenciesStateSource packedDependenciesStateSource)
}
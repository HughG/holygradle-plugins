package holygradle.publishing

import holygradle.unpacking.UnpackModule
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.RepositoryHandler

public interface PublishPackagesExtension {
    void group(String publishGroup)
    
    void name(String publishName)
    
    void nextVersionNumber(String versionNo)
    
    void nextVersionNumber(Closure versionNumClosure)
    
    void nextVersionNumberAutoIncrementFile(String versionNumberFilePath)
    
    void nextVersionNumberEnvironmentVariable(String versionNumberEnvVar)
    
    void repositories(Action<RepositoryHandler> configure)
    
    void republish(Closure closure)

    RepublishHandler getRepublishHandler()

    Task defineCheckTask(Iterable<UnpackModule> unpackModules)
}
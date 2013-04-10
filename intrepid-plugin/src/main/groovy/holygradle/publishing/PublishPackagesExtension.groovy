package holygradle.publishing

public interface PublishPackagesExtension {
    void group(String publishGroup)
    
    void name(String publishName)
    
    void nextVersionNumber(String versionNo)
    
    void nextVersionNumber(Closure versionNumClosure)
    
    void nextVersionNumberAutoIncrementFile(String versionNumberFilePath)
    
    void nextVersionNumberEnvironmentVariable(String versionNumberEnvVar)
    
    void repositories(def configure)
    
    void republish(Closure closure)
}
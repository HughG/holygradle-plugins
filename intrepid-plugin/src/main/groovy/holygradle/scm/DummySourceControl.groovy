package holygradle.scm

import org.gradle.api.Task

class DummySourceControl implements SourceControlRepository {
    Task getToolSetupTask() { null }

    public File getLocalDir() { null }
    
    public String getProtocol() { "n/a" }
    
    public String getUrl() { null }
    
    public String getRevision() { null }
    
    public boolean hasLocalChanges() { false }
}

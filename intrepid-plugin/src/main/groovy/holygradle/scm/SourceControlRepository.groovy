package holygradle.scm

import org.gradle.api.Task

public interface SourceControlRepository {
    /**
     * Returns a task which does any setup of version control tools required for this repository.
     * @return
     */
    Task getToolSetupTask()

    File getLocalDir()
    
    String getProtocol()
    
    String getUrl()
    
    String getRevision()
    
    boolean hasLocalChanges()
}

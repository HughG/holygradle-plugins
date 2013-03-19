package holygradle

import org.gradle.*
import org.gradle.api.*

public interface SourceControlRepository {
    File getLocalDir()
    
    String getProtocol()
    
    String getUrl()
    
    String getRevision()
    
    boolean hasLocalChanges()
}

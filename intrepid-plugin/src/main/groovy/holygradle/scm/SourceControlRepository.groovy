package holygradle.scm

public interface SourceControlRepository {
    File getLocalDir()
    
    String getProtocol()
    
    String getUrl()
    
    String getRevision()
    
    boolean hasLocalChanges()
}

package holygradle.scm

class DummySourceControl implements SourceControlRepository {
    public File getLocalDir() { null }
    
    public String getProtocol() { "n/a" }
    
    public String getUrl() { null }
    
    public String getRevision() { null }
    
    public boolean hasLocalChanges() { false }
}

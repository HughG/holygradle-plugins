package holygradle.artifactorymanager

interface ArtifactoryAPI {
    String getRepository();
    
    Date getNow();
    
    def getFolderInfoJson(String path);
    
    void removeItem(String path);
}
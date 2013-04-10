package holygradle.artifactory_manager

interface ArtifactoryAPI {
    String getRepository();
    
    Date getNow();
    
    def getFolderInfoJson(String path);
    
    void removeItem(String path);
}
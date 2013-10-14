package holygradle.artifactory_manager

interface ArtifactoryAPI {
    String getRepository()
    
    Date getNow()
    
    Map getFolderInfoJson(String path)

    Map getFileInfoJson(String path)

    void removeItem(String path)
}
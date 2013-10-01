package holygradle.artifactory_manager

import org.gradle.api.logging.Logger
import org.gradle.util.ConfigureUtil

class RepositoryHandler {
    private final ArtifactoryManagerHandler artifactoryManager
    private String repository
    private String username
    private String password
    private List<DeleteRequest> deleteRequests = []
    private final Logger logger

    public RepositoryHandler(Logger logger, String repository, ArtifactoryManagerHandler artifactoryManager) {
        this.logger = logger
        this.repository = repository
        this.artifactoryManager = artifactoryManager
    }
    
    public void username(String username) {
        this.username = username
    }
    
    public void password(String password) {
        this.password = password
    }

    public void delete(String module, Closure closure) {
        DeleteRequest deleteRequest = new DeleteRequest(logger, module)
        ConfigureUtil.configure(closure, deleteRequest)
        deleteRequests.add(deleteRequest)
    }
    
    public boolean canDelete() {
        deleteRequests.size() > 0
    }
    
    public void doDelete(boolean dryRun) {
        ArtifactoryAPI artifactoryApi = artifactoryManager.getArtifactoryAPI(repository, username, password, dryRun)
        println "Deleting artifacts in '${artifactoryApi.getRepository()}'."
        for (deleteRequest in deleteRequests) {
            deleteRequest.process(artifactoryApi)
        }
    }
}

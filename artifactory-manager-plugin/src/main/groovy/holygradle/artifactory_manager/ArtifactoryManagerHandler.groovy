package holygradle.artifactory_manager

import org.gradle.*
import org.gradle.api.*
import org.gradle.util.ConfigureUtil
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.artifacts.repositories.IvyArtifactRepository

class ArtifactoryManagerHandler {
    private final Project project
    private String server
    private Closure artifactoryApiFactory
    private RepositoryHandler defaultRepositoryHandler
    private def repositoryHandlers = []
    private def artifactoryAPIs = [:]
    
    public ArtifactoryManagerHandler(Project project) {
        this.project = project
        defaultRepositoryHandler = new RepositoryHandler(null, this)
        repositoryHandlers.add(defaultRepositoryHandler)
    }
    
    public ArtifactoryManagerHandler(ArtifactoryAPI artifactory) {
        artifactoryApiFactory = { repository, username, password, dryRun -> artifactory }
        defaultRepositoryHandler = new RepositoryHandler(null, this)
        repositoryHandlers.add(defaultRepositoryHandler)
    }
    
    public void server(String server) {
        this.server = server
    }
    
    public ArtifactoryAPI getArtifactoryAPI(String repository, String username, String password, boolean dryRun) {
        if (artifactoryApiFactory != null) {
            artifactoryApiFactory(repository, username, password, dryRun)
        } else {
            getArtifactoryAPIDefault(repository, username, password, dryRun)
        }
    }
    
    private ArtifactoryAPI getArtifactoryAPIDefault(String repository, String username, String password, boolean dryRun) {
        if (server == null || repository == null || username == null || password == null) {
            def targetRepos = project.repositories.matching { repo ->
                repo instanceof AuthenticationSupported && repo.getCredentials().getUsername() != null
            }
            
            if (targetRepos.size() == 0) {
                throw new RuntimeException("Must specify one repository for deleting from.")
            } else if (targetRepos.size() > 1) {
                throw new RuntimeException("Specify only one repository to delete from, otherwise we might delete from the wrong place.")
            }
            
            def targetRepo = targetRepos[0]
            if (!targetRepo instanceof IvyArtifactRepository) {
                throw new RuntimeException("Failed to find suitable repository from which to retrieve defaults.")
            }
            
            def url = targetRepo.getUrl()
            def urlMatch = url =~ /(http\w*[:\/]+[\w\.]+)\/.*\/([\w\-]+)/
            if (urlMatch.size() == 0) {
                throw new RuntimeException("Failed to parse URL for server and repository")
            }
            if (server == null) {
                server = urlMatch[0][1]
            }
            if (repository == null) {
                repository = urlMatch[0][2]
            }
            if (username == null) {
                username = targetRepo.getCredentials().getUsername()
            }
            if (password == null) {
                password = targetRepo.getCredentials().getPassword()
            }
        }
        new DefaultArtifactoryAPI(server, repository, username, password, dryRun)
    }
    
    public void delete(Closure closure) {
        defaultRepositoryHandler.delete(closure)
    }
    
    public void delete(String module, Closure closure) {
        defaultRepositoryHandler.delete(module, closure)
    }
    
    public void repository(String repository, Closure closure) {
        def repositoryHandler = new RepositoryHandler(repository, this)
        ConfigureUtil.configure(closure, repositoryHandler)
        repositoryHandlers.add(repositoryHandler)
    }
    
    public boolean canDelete() {
        repositoryHandlers.any { it.canDelete() }
    }
    
    public void doDelete(boolean dryRun) {
        repositoryHandlers.each { 
            if (it.canDelete()) {
                it.doDelete(dryRun)
            }
        }
    }
}
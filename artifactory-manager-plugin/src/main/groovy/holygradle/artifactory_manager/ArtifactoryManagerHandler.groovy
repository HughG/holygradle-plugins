package holygradle.artifactory_manager

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.util.ConfigureUtil

import java.util.regex.Matcher

class ArtifactoryManagerHandler {
    private final Project project
    private String server
    private Closure artifactoryApiFactory
    private RepositoryHandler defaultRepositoryHandler
    private List<RepositoryHandler> repositoryHandlers = []

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
            List<ArtifactRepository> targetRepos = project.repositories.matching { repo ->
                repo instanceof AuthenticationSupported && repo.credentials.username != null
            }
            
            if (targetRepos.size() == 0) {
                throw new RuntimeException("Must specify one repository for deleting from.")
            } else if (targetRepos.size() > 1) {
                throw new RuntimeException("Specify only one repository to delete from, otherwise we might delete from the wrong place.")
            }

            IvyArtifactRepository targetRepo = targetRepos[0] as IvyArtifactRepository
            if (targetRepo == null) {
                throw new RuntimeException("Failed to find suitable repository from which to retrieve defaults.")
            }
            
            URI url = targetRepo.url
            Matcher urlMatch = (url =~ /(http\w*[:\/]+[\w\.]+)\/.*\/([\w\-]+)/)
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
                username = targetRepo.credentials.username
            }
            if (password == null) {
                password = targetRepo.credentials.password
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
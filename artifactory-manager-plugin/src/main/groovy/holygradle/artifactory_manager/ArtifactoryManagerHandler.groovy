package holygradle.artifactory_manager

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.logging.Logger
import org.gradle.util.ConfigureUtil

import java.util.regex.Matcher

class ArtifactoryManagerHandler {
    private final Project project
    private Closure artifactoryApiFactory
    private String server
    private String username
    private String password
    private List<RepositoryHandler> repositoryHandlers = []
    private final Logger logger

    public ArtifactoryManagerHandler(Project project) {
        this.project = project
        this.logger = project.logger
    }
    
    public ArtifactoryManagerHandler(Logger logger, ArtifactoryAPI artifactory) {
        this.logger = logger
        artifactoryApiFactory = { repository, username, password, dryRun -> artifactory }
    }
    
    public void server(String server) {
        this.server = server
    }

    public void username(String username) {
        this.username = username
    }

    public void password(String password) {
        this.password = password
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

    public void repository(String repository, Closure closure) {
        RepositoryHandler repositoryHandler = new RepositoryHandler(logger, repository, this)
        repositoryHandler.username(username)
        repositoryHandler.password(password)
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
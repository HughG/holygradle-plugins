package holygradle.artifactory_manager

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.logging.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ArtifactoryManagerHandler(
        private val project: Project
) {
    companion object {
        private val REPO_URL_REGEX = "(http\\w*[:/]+[\\w.]+)/.*/([\\w\\-]+)".toRegex()
    }
    private var artifactoryApiFactory: ((String?, String?, String?, Boolean) -> ArtifactoryAPI)? = null
    private var server: String? = null
    private lateinit var username: String
    private lateinit var password: String
    private var minRequestIntervalInMillis: Long = 10
    private val repositoryHandlers = mutableListOf<RepositoryHandler>()
    val outputDir: File

    init {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        this.outputDir = File(File(project.buildDir, "listArtifactoryStorageSpace"), dateStr)
    }
    
    constructor(project: Project, artifactory: ArtifactoryAPI): this(project) {
        artifactoryApiFactory = { _, _, _, _ -> artifactory }
    }
    
    fun server(server: String) {
        this.server = server
    }

    fun username(username: String) {
        this.username = username
    }

    fun password(password: String) {
        this.password = password
    }

    fun minRequestIntervalInMillis(interval: Long) {
        this.minRequestIntervalInMillis = interval
    }

    fun getArtifactoryAPI(repository: String?, username: String?, password: String?, dryRun: Boolean): ArtifactoryAPI {
        val factory = artifactoryApiFactory
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // IntelliJ complains with or without "!!"
        val getApi = if (factory != null) factory!!::invoke else this::getArtifactoryAPIDefault
        return getApi(repository, username, password, dryRun)
    }
    
    private fun getArtifactoryAPIDefault(repository: String?, username: String?, password: String?, dryRun: Boolean): ArtifactoryAPI {
        val server = this.server
        if (server == null || repository == null || username == null || password == null) {
            val targetRepos = project.repositories.matching { repo ->
                repo is AuthenticationSupported && repo.credentials.username != null
            }

            if (targetRepos.size == 0) {
                throw RuntimeException("Must specify one repository for deleting from.")
            } else if (targetRepos.size > 1) {
                throw RuntimeException("Specify only one repository to delete from, otherwise we might delete from the wrong place.")
            }

            val targetRepo = targetRepos[0] as? IvyArtifactRepository
                    ?: throw RuntimeException("Failed to find suitable repository from which to retrieve defaults.")

            val url = targetRepo.url
            val result = REPO_URL_REGEX.matchEntire(url.toString())
                    ?: throw RuntimeException("Failed to parse URL for server and repository")

            return DefaultArtifactoryAPI(
                    server ?: result.groupValues[1],
                    repository ?: result.groupValues[2],
                    username ?: targetRepo.credentials.username,
                    password ?: targetRepo.credentials.password,
                    dryRun
            )
        } else {
            return DefaultArtifactoryAPI(server, repository, username, password, dryRun)
        }
    }

    fun repository(repository: String, closure: Action<RepositoryHandler>) {
        val repositoryHandler =
            RepositoryHandler(project.logger, repository, this, outputDir, minRequestIntervalInMillis)
        repositoryHandler.username(username)
        repositoryHandler.password(password)
        closure.execute(repositoryHandler)
        repositoryHandlers.add(repositoryHandler)
    }

    val canDelete: Boolean
        get() = repositoryHandlers.any { it.canDelete }

    fun doDelete(dryRun: Boolean) {
        for (it in repositoryHandlers) {
            if (it.canDelete) {
                it.doDelete(dryRun)
            }
        }
    }

    fun listStorage() {
        for (it in repositoryHandlers) {
            it.listStorage()
        }
    }
}
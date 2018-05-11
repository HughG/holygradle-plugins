package holygradle.artifactory_manager

import org.gradle.api.Action
import org.gradle.api.logging.Logger
import java.io.File
import java.io.IOException

class RepositoryHandler(
        private val logger: Logger,
        private val repository: String,
        private val artifactoryManager: ArtifactoryManagerHandler,
        private val outputDir: File,
        private val minRequestIntervalInMillis: Long
) {
    private var username: String? = null
    private var password: String? = null
    private val deleteRequests = mutableListOf<DeleteRequest>()

    fun username(username: String) {
        this.username = username
    }
    
    fun password(password: String) {
        this.password = password
    }

    fun delete(module: String, action: Action<DeleteRequest>) {
        val deleteRequest = DeleteRequest(logger, module, minRequestIntervalInMillis)
        action.execute(deleteRequest)
        deleteRequests.add(deleteRequest)
    }

    val canDelete: Boolean
        get() = deleteRequests.isNotEmpty()

    fun doDelete(dryRun: Boolean) {
        val artifactoryApi = artifactoryManager.getArtifactoryAPI(repository, username, password, dryRun)
        println("Deleting artifacts in '${artifactoryApi.repository}'.")
        for (deleteRequest in deleteRequests) {
            deleteRequest.process(artifactoryApi)
        }
    }

    fun listStorage() {
        val artifactoryApi = artifactoryManager.getArtifactoryAPI(repository, username, password, false)
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw IOException("Failed to create ${outputDir} to hold output file")
            }
        }
        val sizesFile = File(outputDir, "${repository}-sizes.txt")
        val moduleSizesFile = File(outputDir, "${repository}-module-sizes.txt")
        val versionSizesFile = File(outputDir, "${repository}-version-sizes.txt")
        logger.lifecycle("Writing size information for ${repository} to ${sizesFile}")
        sizesFile.printWriter().use { sizesWriter ->
            moduleSizesFile.printWriter().use { moduleSizesWriter ->
                versionSizesFile.printWriter().use { versionSizesWriter ->
                    StorageSpaceLister(
                        logger,
                        artifactoryApi,
                        sizesWriter,
                        moduleSizesWriter,
                        versionSizesWriter,
                        minRequestIntervalInMillis
                    ).listStorage()
                }
            }
        }
    }
}

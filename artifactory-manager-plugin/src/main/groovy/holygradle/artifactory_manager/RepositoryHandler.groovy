package holygradle.artifactory_manager

import org.gradle.api.logging.Logger
import org.gradle.util.ConfigureUtil

class RepositoryHandler {
    private final Logger logger
    private final String repository
    private final ArtifactoryManagerHandler artifactoryManager
    private final File outputDir
    private final long minRequestIntervalInMillis
    private String username
    private String password
    private final List<DeleteRequest> deleteRequests = []

    public RepositoryHandler(
        Logger logger,
        String repository,
        ArtifactoryManagerHandler artifactoryManager,
        File outputDir,
        long minRequestIntervalInMillis
    ) {
        this.logger = logger
        this.repository = repository
        this.artifactoryManager = artifactoryManager
        this.outputDir = outputDir
        this.minRequestIntervalInMillis = minRequestIntervalInMillis
    }
    
    public void username(String username) {
        this.username = username
    }
    
    public void password(String password) {
        this.password = password
    }

    public void delete(String module, Closure closure) {
        DeleteRequest deleteRequest = new DeleteRequest(logger, module, minRequestIntervalInMillis)
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

    public void listStorage() {
        ArtifactoryAPI artifactoryApi = artifactoryManager.getArtifactoryAPI(repository, username, password, false)
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new IOException("Failed to create ${outputDir} to hold output file")
            }
        }
        final File sizesFile = new File(outputDir, "${repository}-sizes.txt")
        final File moduleSizesFile = new File(outputDir, "${repository}-module-sizes.txt")
        final File versionSizesFile = new File(outputDir, "${repository}-version-sizes.txt")
        logger.lifecycle "Writing size information for ${repository} to ${sizesFile}"
        sizesFile.withPrintWriter { PrintWriter sizesWriter ->
            moduleSizesFile.withPrintWriter { PrintWriter moduleSizesWriter ->
                versionSizesFile.withPrintWriter { PrintWriter versionSizesWriter ->
                    new StorageSpaceLister(
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

package holygradle.artifactory_manager

import org.gradle.api.logging.Logger

class StorageSpaceLister {
    private static final Double BYTES_PER_MB = 1024 * 1024

    private final ArtifactoryAPI artifactory
    private final String repository
    private final long minRequestIntervalInMillis
    private final PrintWriter printWriter
    private final Logger logger

    public StorageSpaceLister(
        Logger logger,
        ArtifactoryAPI artifactory,
        PrintWriter printWriter,
        long minRequestIntervalInMillis
    ) {
        this.logger = logger
        this.printWriter = printWriter
        this.artifactory = artifactory
        this.repository = artifactory.repository
        this.minRequestIntervalInMillis = minRequestIntervalInMillis
    }

    public listStorage() {
        printWriter.println "Type Path Bytes MB"

        long minTimeSinceLastLog = 0

        long totalSize = 0
        for (Map groupInfo in getChildren("/")) {
            final String groupPath = groupInfo["uri"] // Don't need to prefix "/" at top level.
            long groupSize = 0
            for (Map moduleInfo in getChildren(groupPath)) {
                final String modulePath = groupPath + moduleInfo["uri"]
                long moduleSize = 0
                for (Map versionInfo in getChildren(modulePath)) {
                    final String versionPath = modulePath + versionInfo["uri"]
                    long versionSize = 0
                    for (Map artifactInfo in getChildren(versionPath)) {
                        final String artifactPath = versionPath + artifactInfo["uri"]
                        Map artifactFileInfo = artifactory.getFileInfoJson(artifactPath)
                        final long fileSize = Long.parseLong(artifactFileInfo["size"] as String)
                        printWriter.println "File ${artifactPath} ${fileSize} ${fileSize / BYTES_PER_MB}" as String
                        versionSize += fileSize
                        if (minRequestIntervalInMillis > 0) {
                            Thread.sleep(minRequestIntervalInMillis)
                        }

                        // Log something at least once every couple of seconds.
                        minTimeSinceLastLog += minRequestIntervalInMillis
                        if (minTimeSinceLastLog > 2000) {
                            double sizeInMb = fileSize / BYTES_PER_MB
                            logger.lifecycle "... ${artifactPath} approx. ${sizeInMb.trunc(2)} MB"
                            minTimeSinceLastLog = 0;
                        }
                    }
                    printWriter.println "Version ${versionPath} ${versionSize} ${versionSize / BYTES_PER_MB}" as String
                    printWriter.flush()
                    moduleSize += versionSize
                }
                printWriter.println "Module ${modulePath} ${moduleSize} ${moduleSize / BYTES_PER_MB}" as String
                groupSize += moduleSize
            }
            printWriter.println "Group ${groupPath} ${groupSize} ${groupSize / BYTES_PER_MB}" as String
            totalSize += groupSize
        }
        printWriter.println "Repo / ${totalSize} ${totalSize / BYTES_PER_MB}" as String
    }

    private List<Map> getChildren(String folderPath) {
        artifactory.getFolderInfoJson(folderPath)["children"] as List<Map>
    }
}

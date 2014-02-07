package holygradle.artifactory_manager

import org.gradle.api.logging.Logger

class StorageSpaceLister {
    private static final Double BYTES_PER_MB = 1024 * 1024

    private final ArtifactoryAPI artifactory
    private final String repository
    private final long minRequestIntervalInMillis
    private final PrintWriter sizesPrintWriter
    private final PrintWriter moduleSizesPrintWriter
    private final PrintWriter versionSizesPrintWriter
    private final Logger logger

    public StorageSpaceLister(
        Logger logger,
        ArtifactoryAPI artifactory,
        PrintWriter sizesPrintWriter,
        PrintWriter moduleSizesPrintWriter,
        PrintWriter versionSizesPrintWriter,
        long minRequestIntervalInMillis
    ) {
        this.logger = logger
        this.sizesPrintWriter = sizesPrintWriter
        this.moduleSizesPrintWriter = moduleSizesPrintWriter
        this.versionSizesPrintWriter = versionSizesPrintWriter
        this.artifactory = artifactory
        this.repository = artifactory.repository
        this.minRequestIntervalInMillis = minRequestIntervalInMillis
    }

    public listStorage() {
        sizesPrintWriter.println "Type\tPath\tBytes\tMB"

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
                        long fileSize = 0
                        try {
                            fileSize = Long.parseLong(artifactFileInfo["size"] as String)
                            sizesPrintWriter.println "File\t${artifactPath}\t${fileSize}\t${(fileSize / BYTES_PER_MB).trunc(2)}" as String
                        } catch (NumberFormatException ignored) {
                            sizesPrintWriter.println "File\t${artifactPath}\t0\t0\tNumberFormatException: ${artifactFileInfo["size"]}" as String
                        }
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
                    final String versionSizeInfo = "Version\t${versionPath}\t${versionSize}\t${(versionSize / BYTES_PER_MB).trunc(2)}"
                    sizesPrintWriter.println versionSizeInfo
                    versionSizesPrintWriter.println versionSizeInfo
                    sizesPrintWriter.flush()
                    moduleSize += versionSize
                }
                final String moduleSizeInfo = "Module\t${modulePath}\t${moduleSize}\t${(moduleSize / BYTES_PER_MB).trunc(2)}"
                sizesPrintWriter.println moduleSizeInfo
                moduleSizesPrintWriter.println moduleSizeInfo
                versionSizesPrintWriter.flush()
                groupSize += moduleSize
            }
            sizesPrintWriter.println "Group\t${groupPath}\t${groupSize}\t${(groupSize / BYTES_PER_MB).trunc(2)}" as String
            moduleSizesPrintWriter.flush()
            totalSize += groupSize
        }
        sizesPrintWriter.println "Repo\t/\t${totalSize}\t${(totalSize / BYTES_PER_MB).trunc(2)}" as String
    }

    private List<Map> getChildren(String folderPath) {
        artifactory.getFolderInfoJson(folderPath)["children"] as List<Map>
    }
}

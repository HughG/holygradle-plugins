package holygradle.artifactory_manager

import org.gradle.api.logging.Logger
import java.io.PrintWriter

private const val BYTES_PER_MB: Double = 1024.0 * 1024.0

class StorageSpaceLister(
        private val logger: Logger,
        private val artifactory: ArtifactoryAPI,
        private val sizesPrintWriter: PrintWriter,
        private val moduleSizesPrintWriter: PrintWriter,
        private val versionSizesPrintWriter: PrintWriter,
        private val minRequestIntervalInMillis: Long
) {
    fun listStorage() {
        sizesPrintWriter.println("Type\tPath\tBytes\tMB")

        var minTimeSinceLastLog = 0L

        var totalSize = 0L
        for (groupInfo in getChildren("/")) {
            val groupPath = groupInfo.uri // Don't need to prefix "/" at top level.
            var groupSize = 0L
            for (moduleInfo in getChildren(groupPath)) {
                val modulePath = groupPath + moduleInfo.uri
                var moduleSize = 0L
                for (versionInfo in getChildren(modulePath)) {
                    val versionPath = modulePath + versionInfo.uri
                    var versionSize = 0L
                    for (artifactInfo in getChildren(versionPath)) {
                        val artifactPath = versionPath + artifactInfo.uri
                        val artifactFileInfo = artifactory.getFileInfo(artifactPath)
                        var fileSize = 0L
                        try {
                            fileSize = artifactFileInfo.size
                            sizesPrintWriter.println("File\t${artifactPath}\t${fileSize}\t${(fileSize / BYTES_PER_MB).trunc(2)}")
                        } catch (ignored: NumberFormatException) {
                            sizesPrintWriter.println("File\t${artifactPath}\t0\t0\tNumberFormatException: ${artifactFileInfo["size"]}")
                        }
                        versionSize += fileSize
                        if (minRequestIntervalInMillis > 0) {
                            Thread.sleep(minRequestIntervalInMillis)
                        }

                        // Log something at least once every couple of seconds.
                        minTimeSinceLastLog += minRequestIntervalInMillis
                        if (minTimeSinceLastLog > 2000) {
                            val sizeInMb = fileSize / BYTES_PER_MB
                            logger.lifecycle("... ${artifactPath} approx. ${sizeInMb.trunc(2)} MB")
                            minTimeSinceLastLog = 0
                        }
                    }
                    val versionSizeInfo = "Version\t${versionPath}\t${versionSize}\t${(versionSize / BYTES_PER_MB).trunc(2)}"
                    sizesPrintWriter.println(versionSizeInfo)
                    versionSizesPrintWriter.println(versionSizeInfo)
                    sizesPrintWriter.flush()
                    moduleSize += versionSize
                }
                val moduleSizeInfo = "Module\t${modulePath}\t${moduleSize}\t${(moduleSize / BYTES_PER_MB).trunc(2)}"
                sizesPrintWriter.println(moduleSizeInfo)
                moduleSizesPrintWriter.println(moduleSizeInfo)
                versionSizesPrintWriter.flush()
                groupSize += moduleSize
            }
            sizesPrintWriter.println("Group\t${groupPath}\t${groupSize}\t${(groupSize / BYTES_PER_MB).trunc(2)}")
            moduleSizesPrintWriter.flush()
            totalSize += groupSize
        }
        sizesPrintWriter.println("Repo\t/\t${totalSize}\t${(totalSize / BYTES_PER_MB).trunc(2)}")
    }

    private fun getChildren(folderPath: String): List<FolderChildInfo> =
        artifactory.getFolderInfo(folderPath).children
}

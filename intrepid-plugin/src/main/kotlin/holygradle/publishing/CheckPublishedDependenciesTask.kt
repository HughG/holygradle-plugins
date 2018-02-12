package holygradle.publishing

import holygradle.ArtifactoryHelper
import holygradle.unpacking.PackedDependenciesStateSource
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.PasswordCredentials

class CheckPublishedDependenciesTask : DefaultTask() {
    fun initialize(
        packedDependenciesStateSource: PackedDependenciesStateSource,
        repoUrl: String,
        repoCredentials: PasswordCredentials
    ) {
        doLast {
            val helper = ArtifactoryHelper(
                repoUrl,
                repoCredentials.username,
                repoCredentials.password
            )
            val modules = mutableMapOf<String, Boolean>()
            for (module in packedDependenciesStateSource.allUnpackModules) {
                for (versionInfo in module.versions.values) {
                    // Add a trailing slash because, if we request without one, Artifactory will just respond with a
                    // "302 Found" redirect which adds a trailing slash; so this saves us time.
                    val coord = versionInfo.fullCoordinate.replace(":", "/") + '/'
                    modules[versionInfo.fullCoordinate] = helper.artifactExists(coord)
                }
            }
            val tab = modules.keys
                    .map { (it.length + 8) }
                    .max()
                    ?: 0
            logger.lifecycle("The following artifacts are available at '${repoUrl}':")
            modules.forEach { (moduleVersion, available) ->
                var line = "   ${moduleVersion}" + (" ".repeat(tab-moduleVersion.length))
                if (available) {
                    line += "[OK]"
                    logger.lifecycle(line)
                } else {
                    line += "[NOT AVAILABLE]"
                    logger.error(line)
                }
            }
        }
    }
}
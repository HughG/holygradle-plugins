package holygradle.publishing

import holygradle.ArtifactoryHelper
import holygradle.unpacking.PackedDependenciesStateSource
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.jetbrains.kotlin.utils.keysToMap

open class CheckPublishedDependenciesTask : DefaultTask() {
    fun initialize(
        packedDependenciesStateSource: PackedDependenciesStateSource,
        repoUrl: String,
        repo: AuthenticationSupported
    ) {
        doLast {
            val repoCredentials = repo.credentials
            val helper = ArtifactoryHelper(
                repoUrl,
                requireNotNull(repoCredentials.username, { "Null username supplied" }),
                requireNotNull(repoCredentials.password, { "Null password supplied" })
            )
            // Build a set of all URLs first, because the same module might appear more than once, and we don't want to
            // waste time making the same HTTP request more than once.
            val allModuleUrls = mutableSetOf<String>()
            packedDependenciesStateSource.allUnpackModules.values.forEach { modules ->
                modules.forEach { module ->
                    module.versions.values.forEach { versionInfo ->
                        // Add a trailing slash because, if we request without one, Artifactory will just respond with a
                        // "302 Found" redirect which adds a trailing slash; so this saves us time.
                        allModuleUrls.add(versionInfo.fullCoordinate.replace(":", "/") + '/')
                    }
                }
            }
            val moduleAvailability = allModuleUrls.keysToMap { helper.artifactExists(it) }
            val tab = moduleAvailability.keys
                    .map { (it.length + 8) }
                    .max()
                    ?: 0
            logger.lifecycle("The following artifacts are available at '${repoUrl}':")
            moduleAvailability.forEach { (moduleVersion, available) ->
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
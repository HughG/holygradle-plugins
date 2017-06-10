package holygradle.publishing

import holygradle.ArtifactoryHelper
import holygradle.unpacking.PackedDependenciesStateSource
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.PasswordCredentials

class CheckPublishedDependenciesTask extends DefaultTask {
    public void initialize(
        PackedDependenciesStateSource packedDependenciesStateSource,
        String repoUrl,
        PasswordCredentials repoCredentials
    ) {
        doLast {
            ArtifactoryHelper helper = new ArtifactoryHelper(
                repoUrl,
                repoCredentials.getUsername(),
                repoCredentials.getPassword()
            )
            Map<String, Boolean> modules = [:]
            packedDependenciesStateSource.allUnpackModules.each { module ->
                module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                    // Add a trailing slash because, if we request without one, Artifactory will just respond with a
                    // "302 Found" redirect whcih adds a trailing slash; so this saves us time.
                    String coord = versionInfo.getFullCoordinate().replace(":", "/") + '/'
                    modules[versionInfo.getFullCoordinate()] = helper.artifactExists(coord)
                }
            }
            int tab = 0
            modules.each { String moduleVersion, Boolean available ->
                int thisTab = (moduleVersion.length() + 8)
                tab = (thisTab > tab) ? thisTab : tab
            }
            logger.lifecycle "The following artifacts are available at '${repoUrl}':"
            modules.each { String moduleVersion, Boolean available ->
                String line = "   ${moduleVersion}" + (" "*(tab-moduleVersion.length()))
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
package holygradle.publishing

import holygradle.ArtifactoryHelper
import holygradle.unpacking.PackedDependenciesStateSource
import holygradle.unpacking.UnpackModule
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
            // Build a set of all URLs first, because the same module might appear more than once, and we don't want to
            // waste time making the same HTTP request more than once.
            Set<String> allModuleUrls = new HashSet()
            packedDependenciesStateSource.allUnpackModules.values().each { Collection<UnpackModule> modules ->
                modules.each { module ->
                    module.versions.each { String versionStr, UnpackModuleVersion versionInfo ->
                        // Add a trailing slash because, if we request without one, Artifactory will just respond with a
                        // "302 Found" redirect which adds a trailing slash; so this saves us time.
                        allModuleUrls.add(versionInfo.getFullCoordinate().replace(":", "/") + '/')
                    }
                }
            }
            Map<String, Boolean> moduleAvailability = allModuleUrls.collectEntries {
                [it, helper.artifactExists(it)]
            }
            int tab = 0
            moduleAvailability.each { String moduleVersion, Boolean available ->
                int thisTab = (moduleVersion.length() + 8)
                tab = (thisTab > tab) ? thisTab : tab
            }
            logger.lifecycle "The following artifacts are available at '${repoUrl}':"
            moduleAvailability.each { String moduleVersion, Boolean available ->
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
package holygradle.publishing

import holygradle.unpacking.PackedDependenciesStateSource
import holygradle.unpacking.UnpackModule
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory
import holygradle.ArtifactoryHelper

class CheckPublishedDependenciesTask extends DefaultTask {
    private final StyledTextOutput output
    
    CheckPublishedDependenciesTask() {
        output = services.get(StyledTextOutputFactory).create(CheckPublishedDependenciesTask)
    }
    
    public void initialize(
        PackedDependenciesStateSource packedDependenciesStateSource,
        String repoUrl,
        PasswordCredentials repoCredentials
    ) {
        StyledTextOutput localOutput = output // capture private for closure
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
            localOutput.println "The following artifacts are available at '${repoUrl}':"
            moduleAvailability.each { String moduleVersion, Boolean available ->
                String line = "   ${moduleVersion}" + (" "*(tab-moduleVersion.length()))
                if (available) {
                    line += "[OK]"
                    localOutput.withStyle(StyledTextOutput.Style.Success).println(line)
                } else {
                    line += "[NOT AVAILABLE]"
                    localOutput.withStyle(StyledTextOutput.Style.Failure).println(line)
                }
            }
        }
    }
}
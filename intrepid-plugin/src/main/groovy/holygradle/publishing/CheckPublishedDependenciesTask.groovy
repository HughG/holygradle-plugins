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
            localOutput.println "The following artifacts are available at '${repoUrl}':"
            modules.each { String moduleVersion, Boolean available ->
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
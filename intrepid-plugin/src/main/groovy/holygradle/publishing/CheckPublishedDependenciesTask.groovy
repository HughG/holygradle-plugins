package holygradle

import org.gradle.api.DefaultTask
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory
import org.gradle.logging.StyledTextOutput.Style

class CheckPublishedDependenciesTask extends DefaultTask {
    StyledTextOutput output
    
    CheckPublishedDependenciesTask() {
        output = services.get(StyledTextOutputFactory).create(CheckPublishedDependenciesTask)
    }
    
    public void initialize(def unpackModules, def repo) {
        doLast {
            String url = repo.getUrl().toString()
            ArtifactoryHelper helper = new ArtifactoryHelper(
                url,
                repo.getCredentials().getUsername(),
                repo.getCredentials().getPassword()
            )
        
            def modules = [:]
            unpackModules.each { module ->
                module.versions.each { versionStr, versionInfo ->
                    def coord = versionInfo.getFullCoordinate().replace(":", "/")
                    modules[versionInfo.getFullCoordinate()] = helper.artifactExists(coord)
                }
            }
            int tab = 0
            modules.each { moduleVersion, available ->
                int thisTab = (moduleVersion.length() + 8)
                tab = (thisTab > tab) ? thisTab : tab
            }
            output.println "The following artifacts are available at '${url}':"
            modules.each { moduleVersion, available ->
                def line = "   ${moduleVersion}" + (" "*(tab-moduleVersion.length()))
                if (available) {
                    line += "[OK]"
                    output.withStyle(StyledTextOutput.Style.Success).println(line)
                } else {
                    line += "[NOT AVAILABLE]"
                    output.withStyle(StyledTextOutput.Style.Failure).println(line)
                }
            }
        }
    }
}
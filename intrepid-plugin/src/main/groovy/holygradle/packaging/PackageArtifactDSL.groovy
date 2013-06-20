package holygradle.packaging

import org.gradle.api.artifacts.Configuration

public interface PackageArtifactDSL extends PackageArtifactBaseDSL {
    void setConfiguration(String configuration)
}
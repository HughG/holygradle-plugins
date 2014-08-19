package holygradle.packaging

import holygradle.SettingsFileHelper

class PackageArtifactSettingsFileHandler implements PackageArtifactTextFileHandler {
    private final String name
    private Collection<String> includeModules = []

    public PackageArtifactSettingsFileHandler(String name) {
        this.name = name
    }

    public String getName() {
        return name
    }

    public void include(String... modules) {
        includeModules.addAll(modules)
    }
  
    public void writeFile(File targetFile) {
        SettingsFileHelper.writeSettingsFile(targetFile, includeModules)
    }
}
package holygradle

import holygradle.SettingsFileHelper

class PackageArtifactSettingsFileHandler {
    public final String name
    private def includeModules = []

    public PackageArtifactSettingsFileHandler(String name) {
        this.name = name
    }
    
    public void include(String... modules) {
        modules.each { includeModules.add(it) }
    }
  
    public void writeFile(File parentDir) {
        SettingsFileHelper.writeSettingsFile(new File(parentDir, name), includeModules)
    }
}
package holygradle.unpacking

import org.gradle.api.artifacts.ModuleVersionIdentifier

class UnpackModule {
    public String group
    public String name
    public Map<String, UnpackModuleVersion> versions = [:]
    
    UnpackModule(String group, String name) {
        this.group = group
        this.name = name
    }
    
    public boolean matches(ModuleVersionIdentifier moduleVersion) {
        moduleVersion.getGroup() == group && moduleVersion.getName() == name 
    }
    
    public UnpackModuleVersion getVersion(ModuleVersionIdentifier moduleVersion) {
        if (matches(moduleVersion)) {
            return versions[moduleVersion.getVersion()]
        }
        return null
    }

    @Override
    public String toString() {
        return "UnpackModule{" +
            group + ':' + name +
            ", versions=" + versions +
            '}';
    }
}
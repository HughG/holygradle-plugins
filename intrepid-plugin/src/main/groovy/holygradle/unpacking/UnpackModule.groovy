package holygradle.unpacking

import org.gradle.api.Project
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

    /**
     * Returns a collection of objects representing the transitive set of unpacked modules used by a project.
     *
     * @param project The project for which to retrieve the collection of {@link UnpackModule}s.
     * @return The transitive set of unpacked modules used by a project.
     * @deprecated Use {@code project.packedDependenciesState.getAllUnpackModules()} instead.
     */
    @Deprecated
    public static Collection<UnpackModule> getAllUnpackModules(Project project) {
        // TODO 2014-06-10 HughG: Add deprecation warning?
        project.packedDependenciesState.getAllUnpackModules()
    }

    @Override
    public String toString() {
        return "UnpackModule{" +
            group + ':' + name +
            ", versions=" + versions +
            '}';
    }
}
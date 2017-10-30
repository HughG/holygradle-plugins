package holygradle.unpacking

import org.gradle.api.artifacts.Configuration

public interface PackedDependenciesStateSource {
    /**
     * Returns a collection of objects representing the transitive set of unpacked modules used by a project.
     *
     * @return The transitive set of unpacked modules used by a project.
     */
    public Map<Configuration, Collection<UnpackModule>> getAllUnpackModules()
}
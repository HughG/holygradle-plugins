package holygradle.unpacking

import org.gradle.api.artifacts.Configuration

interface PackedDependenciesStateSource {
    /**
     * Returns a collection of objects representing the transitive set of unpacked modules used by a project.
     *
     * @return The transitive set of unpacked modules used by a project.
     */
    val allUnpackModules: Map<Configuration, Collection<UnpackModule>>
}
package holygradle.unpacking

interface PackedDependenciesStateSource {
    /**
     * Returns a collection of objects representing the transitive set of unpacked modules used by a project.
     *
     * @return The transitive set of unpacked modules used by a project.
     */
    val allUnpackModules: Collection<UnpackModule>
}
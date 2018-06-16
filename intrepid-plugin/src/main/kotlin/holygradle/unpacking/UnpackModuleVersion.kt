package holygradle.unpacking

import holygradle.Helper
import holygradle.dependencies.PackedDependencyHandler
import holygradle.util.addingDefault
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import java.io.File

// TODO 2017-07-25 HughG: Convert this to a sealed class with two subclasses, one for direct dependencies (with null
// parent) and one for transitive dependencies (with null packedDependency).
class UnpackModuleVersion(
        val project: Project,
        val moduleVersion: ModuleVersionIdentifier,
        // TODO: will need to track all parents because each one (unpacked to a different location in
        // the central cache) will need to create a link to this module in the cache.
        val parent: UnpackModuleVersion?,
        // This will be null if no such entry exists, which would be the case if this is a transitive dependency.
        var packedDependency: PackedDependencyHandler?
) {
    val includeVersionNumberInPath: Boolean = packedDependency?.pathIncludesVersionNumber ?: false
    // A map from artifacts to sets of configurations that include the artifacts.
    val artifacts = mutableMapOf<ResolvedArtifact, MutableSet<String>>().addingDefault { HashSet<String>() }
    // The set of configurations in the containing project which lead to this module being included.
    val originalConfigurations: MutableSet<String> = mutableSetOf()

    init {
        // Therefore we must have a parent. Not having a parent is an error.
        if (packedDependency == null && parent == null) {
            throw RuntimeException("Module '${moduleVersion}' has no parent module.")
        }
    }

    val includeInfo: String get() {
        return if (includeVersionNumberInPath) {
            "(includes version number)"
        } else {
            "(no version number)"
        }
    }
    
    fun addArtifacts(arts: Iterable<ResolvedArtifact>, originalConf: String) {
        for (art in arts) {
            artifacts[art]!!.add(originalConf)
        }
        originalConfigurations.add(originalConf)
    }

    val hasArtifacts: Boolean get() = !artifacts.keys.isEmpty()

    val fullCoordinate: String get() = "${moduleVersion.group}:${moduleVersion.name}:${moduleVersion.version}"

    /**
     * Returns an {@link UnpackEntry} object which describes how to unpack this module version in the context of the
     * given {@code project}.  This is suitable for passing to
     * {@link SpeedyUnpackManyTask#addEntry(ModuleVersionIdentifier,UnpackEntry)}
     * @param project
     * @return An {@link UnpackEntry} object which describes how to unpack this module version
     */
    val unpackEntry: UnpackEntry
        get() {
            return UnpackEntry(
                    artifacts.keys.map { it.file },
                    unpackDir,
                    (packedDependency?.shouldApplyUpToDateChecks) ?: false,
                    (packedDependency?.shouldMakeReadonly) ?: false
            )
        }

    // This returns the packedDependencies entry which configures some aspects of this module.
    // The entry could be specifically for this module, in the case where a project directly
    // specifies a dependency. Or this entry could be for a 'parent' packedDependency entry 
    // i.e. one which causes this module to be pulled in as a transitive dependency.
    val selfOrAncestorPackedDependency: PackedDependencyHandler
        get() {
            val packedDependency1 = packedDependency
            if (packedDependency1 != null) {
                return packedDependency1
            }

            // If we don't return packedDependency above then this must be a transitive dependency.
            // Therefore we must have a parent. Not having a parent is an error.
            if (parent == null) {
                throw RuntimeException("Error - module '${fullCoordinate}' has no parent module.")
            }
            return parent.selfOrAncestorPackedDependency
        }

    // Return the name of the directory that should be constructed in the workspace for the unpacked artifacts. 
    // Depending on some other configuration, this directory name could be used for a link or a real directory.
    val targetDirName: String
        get() {
            val packedDependency1 = packedDependency
            return if (packedDependency1 != null) {
                packedDependency1.getTargetNameWithVersionNumber(moduleVersion.version)
            } else if (includeVersionNumberInPath) {
                "${moduleVersion.name}-${moduleVersion.version}"
            } else {
                moduleVersion.name
            }
        }

    // Return the full path of the directory that should be constructed in the workspace for the unpacked artifacts. 
    // Depending on some other configuration, this path could be used for a link or a real directory.
    val targetPathInWorkspace: File
        get() {
            val packedDependency1 = packedDependency
            when {
                packedDependency1 != null -> {
                    // If we have a packed dependency then we can directly construct the target path.
                    // We don't need to go looking through transitive dependencies.
                    val targetPath = packedDependency1.getFullTargetPathWithVersionNumber(moduleVersion.version)
                    return File(project.projectDir, targetPath)
                }
                parent != null -> // If we don't return above then this must be a transitive dependency.
                    // Recursively navigate up the parent hierarchy, appending relative paths.
                    return File(
                            parent.targetPathInWorkspace.parentFile,
                            targetDirName
                    )
                else -> throw RuntimeException(
                        "Cannot determine targetPathInWorkspace: no packedDependency and no parent for ${this}")
            }
        }

    // If true this module should be unpacked to the central cache, otherwise it should be unpacked
    // directly to the workspace.
    private val shouldUnpackToCache: Boolean get() = selfOrAncestorPackedDependency.shouldUnpackToCache

    /**
     * If true, a link for this module should be created, to the central cache, otherwise no link should be created.
     * A link should be created if
     * <ul>
     *     <li>the relevant {@code packedDependencies} entry has {@code unpackToCache = true} (the default);</li>
     *     <li>that entry has not had {@code noCreateLinkToCache() called on it; and</li>
     *     <li>this module version actually has any artifacts -- if not, nothing will be unpacked in the cache, so there
     *     will be no folder to which to make a link.</li>
 *     </ul>
     * @return A flag indicating whether to create a link to the unpack cache for this version.
     */
    val shouldCreateLinkToCache: Boolean get() = selfOrAncestorPackedDependency.shouldCreateLinkToCache && hasArtifacts

    // Return the location to which the artifacts will be unpacked. This could be to the global unpack 
    // cache or it could be to somewhere in the workspace.
    val unpackDir: File
        get() {
            return if (shouldUnpackToCache) {
                // Our closest packed-dependency entry (which could be for 'this' module, or any parent module)
                // dictated that we should unpack to the global cache.
                Helper.getGlobalUnpackCacheLocation(project, moduleVersion)
            } else {
                // We're unpacking directly into the workspace.
                targetPathInWorkspace
            }
        }

    override fun toString(): String {
        val targetPath: String = packedDependency?.getFullTargetPathWithVersionNumber(moduleVersion.version) ?: "n/a"
        return "UnpackModuleVersion{" +
            "moduleVersion=" + moduleVersion +
            ", packedDependency target path='" + targetPath +
            "', parentUnpackModuleVersion=" + parent?.moduleVersion +
            '}'
    }
}


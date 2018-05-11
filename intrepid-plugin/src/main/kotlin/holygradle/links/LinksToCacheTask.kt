package holygradle.links

import holygradle.io.Link
import holygradle.unpacking.PackedDependenciesStateSource
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleVersionIdentifier
import java.util.*

/**
 * Task to create directory links into the Holy Gradle unpack cache, for all packed dependencies in a project.  (This
 * used to be done with one task per link, but that required all dependencies to be resolved -- which can be very
 * slow -- before the list of tasks cn be known -- which needs to happen every time you run Gradle.)
 */
open class LinksToCacheTask : DefaultTask() {
    private var initialized = false
    // The versions in this set have been considered for being added to the list of versions to make links for, but
    // may not have been added to the list.  If a version is to be added but is already in this list, we don't need to
    // consider it or its ancestors again.
    private val versionsSeen = mutableSetOf<ModuleVersionIdentifier>()
    // This list will be processed in order.
    private val versionList: LinkedList<UnpackModuleVersion> = LinkedList()
    private lateinit var mode: Mode

    /**
     * Specifies what this task should do with each link it has to consider.
     */
    enum class Mode {
        /** Create the link, replacing any existing link (but not any other type of file). */
        BUILD,
        /** Remove the link (but not any other type of file). */
        CLEAN
    }

    fun initialize(mode: Mode) {
        if (initialized) {
            throw IllegalStateException("LinksToCacheTask is already initialized")
        }
        this.mode = mode
        doLast {
            for (version in versionList) {
                val linkDir = version.targetPathInWorkspace
                val targetDir = version.unpackDir

                when (mode) {
                    Mode.BUILD -> Link.rebuild(linkDir, targetDir)
                    Mode.CLEAN -> Link.delete(linkDir)
                }
            }
        }
        initialized = true
    }

    /**
     * Adds all {@link UnpackModuleVersion}s from the {@code source} to the list of versions for which this task will
     * create links.
     * @param source A source of {@link UnpackModuleVersion}s.
     */
    fun addUnpackModuleVersions(source: PackedDependenciesStateSource) {
        doFirst {
            source.allUnpackModules.forEach { module ->
                module.versions.values.forEach { versionInfo ->
                    addUnpackModuleVersionWithAncestors(versionInfo)
                }
            }
        }
    }

    /**
     * Adds {@code version} to the list of versions for which this task will create links.  If the version is
     * already present, this method does nothing.
     */
    fun addUnpackModuleVersionWithAncestors(version: UnpackModuleVersion) {
        logger.debug("LinksToCacheTask#addUnpackModuleVersionWithAncestors: adding ${version}")
        if (!maybeAddSingleUnpackModuleVersion(version)) {
            return
        }
        // Now we recursively travel up the parent chain, re-adding each parent.  This means that each ancestor will
        // appear before its descendants, which ensures that links exist before any attempts to create links
        // *inside* those linked folders.  We remove the parent first, in case it is already in the list, so that we
        // don't process it twice.
        val parent = version.parent
        if (parent != null) {
            removeUnpackModuleVersionIfPresent(parent)
            addUnpackModuleVersionWithAncestors(parent)
        }
    }

    /**
     * Adds {@code version} to the list of versions for which this task will create links, unless the version has
     * already been seen, or it doesn't want a link.
     * @return true if the version hasn't been seen before, otherwise false
     */
    private fun maybeAddSingleUnpackModuleVersion(version: UnpackModuleVersion): Boolean {
        val versionNotSeenYet = versionsSeen.add(version.moduleVersion)
        if (versionNotSeenYet) {
            if (version.shouldCreateLinkToCache) {
                versionList.addFirst(version)
            } else {
                logger.debug("LinksToCacheTask#maybeAddSingleUnpackModuleVersion: not creating link for ${version.moduleVersion}")
            }
        } else {
            logger.debug("LinksToCacheTask#maybeAddSingleUnpackModuleVersion: skipping already-present ${version.moduleVersion}")
        }
        return versionNotSeenYet
    }

    /**
     * Removes {@code version} from the list of versions for which this task will create links.  If the version is
     * NOT already present, this method does nothing.
     */
    private fun removeUnpackModuleVersionIfPresent(version: UnpackModuleVersion) {
        if (versionsSeen.remove(version.moduleVersion)) {
            versionList.remove(version)
        }
    }

    /**
     * Returns a read-only copy of the current list of versions, in the order in which they will be processed.  (This
     * is really only public for testing purposes.  We could instead test by making the Link class non-static and
     * injecting a spy version, but that seems unnecessary effort for now.)
     * @return
     */
    val orderedVersions: Collection<UnpackModuleVersion>
        get() {
            @Suppress("UNCHECKED_CAST") // because clone() guarantees to return the right type
            return versionList.clone() as Collection<UnpackModuleVersion>
        }
}

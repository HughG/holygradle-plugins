package holygradle.links

import com.google.common.annotations.VisibleForTesting
import holygradle.io.Link
import holygradle.unpacking.PackedDependenciesStateSource
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.DefaultTask
import java.io.File

/**
 * Task to create directory links into the Holy Gradle unpack cache, for all packed dependencies in a project.  (This
 * used to be done with one task per link, but that required all dependencies to be resolved -- which can be very
 * slow -- before the list of tasks cn be known -- which needs to happen every time you run Gradle.)
 */
open class LinksToCacheTask : DefaultTask() {
    private var initialized = false
    private lateinit var mode: Mode
    // A map from the location of a link to the target location for that link.
    private val links = mutableMapOf<File, File>()
    @VisibleForTesting val linksForTesting: Map<File, File> = links

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
            links.forEach { linkDir, targetDir ->
                LinkTask.checkIsLinkOrMissing(linkDir, targetDir)

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
            source.allUnpackModules.values.forEach { modules ->
                modules.forEach { module ->
                    module.versions.values.forEach { version ->
                        addUnpackModuleVersion(version)
                    }
                }
            }
        }
    }

    /**
     * Adds {@code version} to the list of versions for which this task will create links.  If the version is
     * already present, this method does nothing.
     */
    fun addUnpackModuleVersion(version: UnpackModuleVersion) {
            if (version.shouldCreateLinkToCache) {
                logger.debug("LinksToCacheTask#addUnpackModuleVersion: adding ${version}")
                val linkDir = version.targetPathInWorkspace
                val targetDir = version.linkDir
                links[linkDir] = targetDir
            } else {
                logger.debug("LinksToCacheTask#addUnpackModuleVersion: skipping ${version}")
            }
    }
}

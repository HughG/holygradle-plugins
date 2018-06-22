package holygradle.links

import holygradle.io.Link
import holygradle.unpacking.PackedDependenciesStateSource
import holygradle.unpacking.UnpackModule
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleVersionIdentifier

/**
 * Task to create directory links into the Holy Gradle unpack cache, for all packed dependencies in a project.  (This
 * used to be done with one task per link, but that required all dependencies to be resolved -- which can be very
 * slow -- before the list of tasks cn be known -- which needs to happen every time you run Gradle.)
 */
class LinksToCacheTask extends DefaultTask {
    private boolean initialized = false
    private Mode mode
    // A map from the location of a link to the target location for that link.
    private final Map<File, File> links = new HashMap<>()

    /**
     * Specifies what this task should do with each link it has to consider.
     */
    public enum Mode {
        /** Create the link, replacing any existing link (but not any other type of file). */
        BUILD,
        /** Remove the link (but not any other type of file). */
        CLEAN
    }

    public void initialize(Mode mode) {
        if (initialized) {
            throw new IllegalStateException("LinksToCacheTask is already initialized")
        }
        this.mode = mode
        Map<File, File> localLinks = links // capture private for closure
        doLast {
            localLinks.each { File linkDir, File targetDir ->
                LinkTask.checkIsLinkOrMissing(linkDir, targetDir)

                switch (mode) {
                    case Mode.BUILD:
                        Link.rebuild(linkDir, targetDir)
                        break
                    case Mode.CLEAN:
                        Link.delete(linkDir)
                        break
                    default:
                        throw new IllegalStateException("Unknown LinksToCacheTask mode ${mode}")
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
    public void addUnpackModuleVersions(PackedDependenciesStateSource source) {
        doFirst {
            source.allUnpackModules.values().each { Collection<UnpackModule> modules ->
                modules.each { UnpackModule module ->
                    module.versions.values().each { UnpackModuleVersion version ->
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
    public void addUnpackModuleVersion(UnpackModuleVersion version) {
        if (version.shouldCreateLinkToCache()) {
            logger.debug "LinksToCacheTask#addUnpackModuleVersion: adding ${version}"
            final File linkDir = version.getTargetPathInWorkspace()
            final File targetDir = version.getLinkDir()
            links[linkDir] = targetDir
        } else {
            logger.debug "LinksToCacheTask#addUnpackModuleVersion: skipping ${version}"
        }
    }

    /**
     * Returns a read-only copy of the current list of versions, in the order in which they will be processed.  (This
     * is really only public for testing purposes.  We could instead test by making the Link class non-static and
     * injecting a spy version, but that seems unnecessary effort for now.)
     * @return
     */
    public Map<File, File> getLinks() {
        return links.asImmutable()
    }
}

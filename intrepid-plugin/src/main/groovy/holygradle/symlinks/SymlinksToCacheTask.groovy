package holygradle.symlinks

import holygradle.custom_gradle.util.Symlink
import holygradle.unpacking.PackedDependenciesStateSource
import holygradle.unpacking.UnpackModule
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleVersionIdentifier

/**
 * Task to create symlinks into the Holy Gradle unpack cache, for all packed dependencies in a project.  (This used to
 * be done with one task per symlink, but that required all dependencies to be resolved -- which can be very slow --
 * before the list of tasks cn be known -- which needs to happen every time you run Gradle.)
 */
class SymlinksToCacheTask extends DefaultTask {
    private boolean initialized = false
    // The versions in this set have been considered for being added to the list of versions to make symlinks for, but
    // may not have been added to the list.  If a version is to be added but is already in this list, we don't need to
    // consider it or its ancestors again.
    private final Set<ModuleVersionIdentifier> versionsSeen = new HashSet<ModuleVersionIdentifier>()
    // This list will be processed in order.
    private final LinkedList<UnpackModuleVersion> versionList = new LinkedList<UnpackModuleVersion>()
    private Mode mode

    /**
     * Specifies what this task should do with each symlink it has to consider.
     */
    public enum Mode {
        /** Create the symlink, replacing any existing symlink (but not any other type of file). */
        BUILD,
        /** Remove the symlink (but not any other type of file). */
        CLEAN
    }

    public void initialize(Mode mode) {
        if (initialized) {
            throw new IllegalStateException("SymlinksToCacheTask is already initialized")
        }
        this.mode = mode
        LinkedList<UnpackModuleVersion> localVersionList = versionList // capture private for closure
        doLast {
            localVersionList.each { UnpackModuleVersion version ->
                File linkDir = version.getTargetPathInWorkspace(project)
                File targetDir = version.getSymlinkDir(project)

                SymlinkTask.checkIsSymlinkOrMissing(linkDir, targetDir)

                switch (mode) {
                    case Mode.BUILD:
                        Symlink.rebuild(linkDir, targetDir)
                        break;
                    case Mode.CLEAN:
                        Symlink.delete(linkDir)
                        break;
                    default:
                        throw new IllegalStateException("Unknown SymlinksToCacheTask mode ${mode}")
                }
            }
        }
        initialized = true
    }

    /**
     * Adds all {@link UnpackModuleVersion}s from the {@code source} to the list of versions for which this task will
     * create symlinks.
     * @param source A source of {@link UnpackModuleVersion}s.
     */
    public void addUnpackModuleVersions(PackedDependenciesStateSource source) {
        doFirst {
            source.allUnpackModules.each { UnpackModule module ->
                module.versions.values().each { UnpackModuleVersion versionInfo ->
                    addUnpackModuleVersionWithAncestors(versionInfo)
                }
            }
        }
    }

    /**
     * Adds {@code version} to the list of versions for which this task will create symlinks.  If the version is
     * already present, this method does nothing.
     */
    public void addUnpackModuleVersionWithAncestors(UnpackModuleVersion version) {
        logger.debug "SymlinksToCacheTask#addUnpackModuleVersionWithAncestors: adding ${version}"
        if (!maybeAddSingleUnpackModuleVersion(version)) {
            return
        }
        // Now we recursively travel up the parent chain, re-adding each parent.  This means that each ancestor will
        // appear before its descendants, which ensures that symlinks exist before any attempts to create symlinks
        // *inside* those symlinked folders.  We remove the parent first, in case it is already in the list, so that we
        // don't process it twice.
        Collection<UnpackModuleVersion> parents = version.parents
        parents.each {
            UnpackModuleVersion parent ->
                removeUnpackModuleVersionIfPresent(parent)
                addUnpackModuleVersionWithAncestors(parent)

        }
    }

    /**
     * Adds {@code version} to the list of versions for which this task will create symlinks, unless the version has
     * already been seen, or it doesn't want a symlink.
     * @return true if the version hasn't been seen before, otherwise false
     */
    private boolean maybeAddSingleUnpackModuleVersion(UnpackModuleVersion version) {
        final boolean versionNotSeenYet = versionsSeen.add(version.moduleVersion)
        if (versionNotSeenYet) {
            if (version.shouldCreateSymlinkToCache()) {
                versionList.addFirst(version)
            } else {
                logger.debug "SymlinksToCacheTask#maybeAddSingleUnpackModuleVersion: not creating symlink for ${version.moduleVersion}"
            }
        } else {
            logger.debug "SymlinksToCacheTask#maybeAddSingleUnpackModuleVersion: skipping already-present ${version.moduleVersion}"
        }
        return versionNotSeenYet
    }

    /**
     * Removes {@code version} from the list of versions for which this task will create symlinks.  If the version is
     * NOT already present, this method does nothing.
     */
    public void removeUnpackModuleVersionIfPresent(UnpackModuleVersion version) {
        if (versionsSeen.remove(version.moduleVersion)) {
            versionList.remove(version)
        }
    }

    /**
     * Returns a read-only copy of the current list of versions, in the order in which they will be processed.  (This
     * is really only public for testing purposes.  We could instead test by making the Symlink class non-static and
     * injecting a spy version, but that seems unnecessary effort for now.)
     * @return
     */
    public Collection<UnpackModuleVersion> getOrderedVersions() {
        return versionList.clone() as Collection<UnpackModuleVersion>
    }
}

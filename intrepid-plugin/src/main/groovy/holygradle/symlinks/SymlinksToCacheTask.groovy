package holygradle.symlinks

import holygradle.custom_gradle.util.Symlink
import holygradle.unpacking.PackedDependenciesStateSource
import holygradle.unpacking.UnpackModule
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleVersionIdentifier

class SymlinksToCacheTask extends DefaultTask {
    private boolean initialized = false
    private final Set<ModuleVersionIdentifier> versions = new HashSet<ModuleVersionIdentifier>()
    // This list will be processed in order.
    private final LinkedList<UnpackModuleVersion> versionList = new LinkedList<UnpackModuleVersion>()
    private Mode mode

    public enum Mode {
        BUILD,
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
                File targetDir = version.getUnpackDir(project)
                final boolean linkExists = linkDir.exists()
                final boolean isSymlink = Symlink.isJunctionOrSymlink(linkDir)
                if (linkExists && !isSymlink) {
                    throw new RuntimeException(
                        "Could not create a symlink from '${linkDir.path}' to '${targetDir.path}' " +
                        "because the former already exists and is not a symlink."
                    )
                }

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

    public void addUnpackModuleVersions(PackedDependenciesStateSource source) {
        doFirst {
            source.allUnpackModules.each { UnpackModule module ->
                module.versions.values().each { UnpackModuleVersion versionInfo ->
                    // Symlink from workspace to the unpack cache, if the dependency was unpacked to the
                    // unpack cache (as opposed to unpacked directly to the workspace).
                    versionInfo.addToSymlinkTaskIfRequired(this)
                }
            }
        }
    }

    public void addUnpackModuleVersion(UnpackModuleVersion version) {
        if (!versions.add(version.moduleVersion)) {
            throw new RuntimeException(
                "Cannot initialize symlink to cache for '${version.moduleVersion}' (${version})" +
                "because initialization has already been done for that module version."
            )
        }
        versionList.addFirst(version)
        // Now we travel up the parent chain and, for each ancestor, we add it *before* its descendants (i.e., at the
        // start of the list).  This ensures that symlinks exist before any attempts to create symlinks *inside* those
        // symlinked folders.  We remove the parent first, in case it is already in the list, so that we don't process
        // it twice.
        for (UnpackModuleVersion parent = version.parent; parent != null; parent = parent.parent) {
            versionList.remove(parent)
            versionList.addFirst(parent)
        }
    }
}

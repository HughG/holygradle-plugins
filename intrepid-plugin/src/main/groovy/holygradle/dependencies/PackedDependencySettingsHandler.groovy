package holygradle.dependencies

import org.gradle.api.Project

/**
 * Extension to hold project-wide settings which affect how packed dependencies are handled.
 */
class PackedDependencySettingsHandler {
    private static boolean DEFAULT_USE_RELATIVE_PATH_FROM_IVY_XML = false
    private static String DEFAULT_UNPACK_CACHE_DIR = "unpackCache"

    private Boolean useRelativePathFromIvyXml = null
    private File unpackedDependenciesCacheDir = null
    private final Project project

    public static PackedDependencySettingsHandler findPackedDependencySettings(Project project) {
        return project.extensions.findByName("packedDependencySettings") as PackedDependencySettingsHandler
    }

    public static PackedDependencySettingsHandler findOrCreatePackedDependencySettings(Project project) {
        return findPackedDependencySettings(project) ?:
            (project.extensions.create("packedDependencySettings", PackedDependencySettingsHandler, project)
                as PackedDependencySettingsHandler)
    }

    public static PackedDependencySettingsHandler getPackedDependencySettings(Project project) {
        PackedDependencySettingsHandler ext =
            project.extensions.findByName("packedDependencySettings") as PackedDependencySettingsHandler
        if (ext == null) {
            throw new RuntimeException("Failed to find packedDependencySettings extension on " + project)
        }
        return ext
    }

    PackedDependencySettingsHandler(Project project) {
        this.project = project
    }

    /*
     * Returns the root project's extension (if it's been added), or null (if not added, or if this extension is on the
     * root project.
     */
    private PackedDependencySettingsHandler getFallback() {
        if (project == project.rootProject) {
            return null
        } else {
            return findPackedDependencySettings(project.rootProject)
        }
    }

    public void setUseRelativePathFromIvyXml(boolean defaultFail) {
        useRelativePathFromIvyXml = defaultFail
    }

    /**
     * If {@code true}, the intrepid plugin will use an old method to decide where to put the transitive dependencies of
     * each packed dependency -- it will use a custom, non-namespaced {@code relativePath} attribute on each {@code
     * &lt;dependency/&gt;} element in the {@code ivy.xml};
     * if unset (null), use the value from the root project (defaulting to false);
     * if false, use the current behaviour, which is to put transitive dependencies as siblings of the dependency, using
     * the module name as the folder name.
     */
    public boolean getUseRelativePathFromIvyXml() {
        // Fall back to the settings from the root project, if its extension has already been added there.  (It normally
        // will have, but not if the evaluation order is changed with "evaluationDependsOn" or similar.)
        if (useRelativePathFromIvyXml != null) {
            return useRelativePathFromIvyXml
        }
        PackedDependencySettingsHandler fallback = getFallback()
        if (fallback != null) {
            return fallback.getUseRelativePathFromIvyXml()
        }
        return DEFAULT_USE_RELATIVE_PATH_FROM_IVY_XML
    }

    public void setUnpackedDependenciesCacheDir(File unpackCacheDir) {
        unpackedDependenciesCacheDir = unpackCacheDir
    }

    /**
     * If set, the intrepid plugin will unpack dependencies unto subfolders of the given folder;
     * if unset (null), use the value from the root project (defaulting to "{@code $GRADLE_USER_HOME/unpackCache}").
     */
    public boolean getUnpackedDependenciesCacheDir() {
        // Fall back to the settings from the root project, if its extension has already been added there.  (It normally
        // will have, but not if the evaluation order is changed with "evaluationDependsOn" or similar.)
        if (unpackedDependenciesCacheDir != null) {
            return unpackedDependenciesCacheDir
        }
        PackedDependencySettingsHandler fallback = getFallback()
        if (fallback != null) {
            return fallback.getUnpackedDependenciesCacheDir()
        }
        return new File(project.gradle.gradleUserHomeDir, DEFAULT_UNPACK_CACHE_DIR)
    }
}

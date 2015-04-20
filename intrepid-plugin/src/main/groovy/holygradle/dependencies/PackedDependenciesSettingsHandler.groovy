package holygradle.dependencies

import org.gradle.api.Project

/**
 * Extension to hold project-wide settings which affect how packed dependencies are handled.
 */
class PackedDependenciesSettingsHandler {
    public static final String PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME = "packedDependenciesSettings"
    private static boolean DEFAULT_USE_RELATIVE_PATH_FROM_IVY_XML = false
    private static String DEFAULT_UNPACK_CACHE_DIR = "unpackCache"

    private Boolean useRelativePathFromIvyXml = null
    private File unpackedDependenciesCacheDir = null
    private final Project project

    public static PackedDependenciesSettingsHandler findPackedDependenciesSettings(Project project) {
        return project.extensions.findByName(PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME) as PackedDependenciesSettingsHandler
    }

    public static PackedDependenciesSettingsHandler findOrCreatePackedDependenciesSettings(Project project) {
        return findPackedDependenciesSettings(project) ?:
            (project.extensions.create(PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME, PackedDependenciesSettingsHandler, project)
                as PackedDependenciesSettingsHandler)
    }

    public static PackedDependenciesSettingsHandler getPackedDependenciesSettings(Project project) {
        PackedDependenciesSettingsHandler ext =
            project.extensions.findByName(PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME) as PackedDependenciesSettingsHandler
        if (ext == null) {
            throw new RuntimeException("Failed to find ${PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME} extension on " + project)
        }
        return ext
    }

    PackedDependenciesSettingsHandler(Project project) {
        this.project = project
    }

    /*
     * Returns the root project's extension (if it's been added), or null (if not added, or if this extension is on the
     * root project.
     */
    private PackedDependenciesSettingsHandler getFallback() {
        if (project == project.rootProject) {
            return null
        } else {
            return findPackedDependenciesSettings(project.rootProject)
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
        PackedDependenciesSettingsHandler fallback = getFallback()
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
    public File getUnpackedDependenciesCacheDir() {
        // Fall back to the settings from the root project, if its extension has already been added there.  (It normally
        // will have, but not if the evaluation order is changed with "evaluationDependsOn" or similar.)
        if (unpackedDependenciesCacheDir != null) {
            return unpackedDependenciesCacheDir
        }
        PackedDependenciesSettingsHandler fallback = getFallback()
        if (fallback != null) {
            return fallback.getUnpackedDependenciesCacheDir()
        }
        return new File(project.gradle.gradleUserHomeDir, DEFAULT_UNPACK_CACHE_DIR)
    }
}

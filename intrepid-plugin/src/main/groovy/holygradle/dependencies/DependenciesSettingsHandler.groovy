package holygradle.dependencies

import org.gradle.api.Project

/**
 * Extension to hold project-wide settings which affect how dependencies are handled.
 */
class DependenciesSettingsHandler {
    public static final String DEPENDENCIES_SETTINGS_HANDLER_NAME = "dependenciesSettings"
    private static boolean DEFAULT_FAIL_ON_VERSION_CONFLICT_DEFAULT = true

    private Boolean failOnVersionConflict = null
    private final Project project

    public static DependenciesSettingsHandler findDependenciesSettings(Project project) {
        return project.extensions.findByName(DEPENDENCIES_SETTINGS_HANDLER_NAME) as DependenciesSettingsHandler
    }

    public static DependenciesSettingsHandler findOrCreateDependenciesSettings(Project project) {
        return findDependenciesSettings(project) ?:
            (project.extensions.create(DEPENDENCIES_SETTINGS_HANDLER_NAME, DependenciesSettingsHandler, project)
                as DependenciesSettingsHandler)
    }

    public static DependenciesSettingsHandler getDependenciesSettings(Project project) {
        DependenciesSettingsHandler ext =
            project.extensions.findByName(DEPENDENCIES_SETTINGS_HANDLER_NAME) as DependenciesSettingsHandler
        if (ext == null) {
            throw new RuntimeException("Failed to find ${DEPENDENCIES_SETTINGS_HANDLER_NAME} extension on " + project)
        }
        return ext
    }

    DependenciesSettingsHandler(Project project) {
        this.project = project
    }

    /*
     * Returns the root project's extension (if it's been added), or null (if not added, or if this extension is on the
     * root project.
     */
    private DependenciesSettingsHandler getFallback() {
        if (project == project.rootProject) {
            return null
        } else {
            return findDependenciesSettings(project.rootProject)
        }
    }

    public void setDefaultFailOnVersionConflict(boolean defaultFail) {
        failOnVersionConflict = defaultFail
    }

    /**
     * If {@code true}, the intrepid plugin will call
     * {@link org.gradle.api.artifacts.ResolutionStrategy#failOnVersionConflict} for each
     * {@link org.gradle.api.artifacts.Configuration} as it is created (unless the "dependencies" or "dependencyInsight"
     * tasks are being run);
     * if unset (null), use the value from the root project (defaulting to true);
     * if false, don't call {@link org.gradle.api.artifacts.ResolutionStrategy#failOnVersionConflict}.
     */
    public boolean getDefaultFailOnVersionConflict() {
        // Fall back to the settings from the root project, if its extension has already been added there.  (It normally
        // will have, but not if the evaluation order is changed with "evaluationDependsOn" or similar.)
        if (failOnVersionConflict != null) {
            return failOnVersionConflict
        }
        DependenciesSettingsHandler fallback = getFallback()
        if (fallback != null) {
            return fallback.getDefaultFailOnVersionConflict()
        }
        return DEFAULT_FAIL_ON_VERSION_CONFLICT_DEFAULT
    }
}

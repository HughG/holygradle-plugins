package holygradle.dependencies

import org.gradle.api.Project

/**
 * Extension to hold project-wide settings which affect how dependencies are handled.
 */
class DependencySettingsExtension {
    private static boolean DEFAULT_FAIL_ON_VERSION_CONFLICT_DEFAULT = true

    private Boolean failOnVersionConflict = null
    private final Project project

    public static DependencySettingsExtension findDependencySettings(Project project) {
        return project.extensions.findByName("dependencySettings") as DependencySettingsExtension
    }

    public static DependencySettingsExtension findOrCreateDependencySettings(Project project) {
        return findDependencySettings(project) ?:
            (project.extensions.create("dependencySettings", DependencySettingsExtension, project)
                as DependencySettingsExtension)
    }

    public static DependencySettingsExtension getDependencySettings(Project project) {
        DependencySettingsExtension ext =
            project.extensions.findByName("dependencySettings") as DependencySettingsExtension
        if (ext == null) {
            throw new RuntimeException("Failed to find dependencySettings extension on " + project)
        }
        return ext
    }

    DependencySettingsExtension(Project project) {
        this.project = project
    }

    /*
     * Returns the root project's extension (if it's been added), or null (if not added, or if this extension is on the
     * root project.
     */
    private DependencySettingsExtension getFallback() {
        if (project == project.rootProject) {
            return null
        } else {
            return findDependencySettings(project.rootProject)
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
        return failOnVersionConflict ?:
            getFallback()?.defaultFailOnVersionConflict ?:
            DEFAULT_FAIL_ON_VERSION_CONFLICT_DEFAULT
    }
}

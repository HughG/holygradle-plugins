package holygradle.dependencies

import org.gradle.api.Project

/**
 * Extension to hold project-wide settings which affect how dependencies are handled.
 */
open class DependenciesSettingsHandler(private val project: Project) {
    companion object {
        private const val DEPENDENCIES_SETTINGS_HANDLER_NAME = "dependenciesSettings"
        private const val DEFAULT_FAIL_ON_VERSION_CONFLICT_DEFAULT = true

        @JvmStatic
        fun findDependenciesSettings(project: Project): DependenciesSettingsHandler? {
            return project.extensions.findByName(DEPENDENCIES_SETTINGS_HANDLER_NAME) as? DependenciesSettingsHandler
        }

        @JvmStatic
        fun findOrCreateDependenciesSettings(project: Project): DependenciesSettingsHandler {
            return findDependenciesSettings(project) ?:
                    (project.extensions.create(DEPENDENCIES_SETTINGS_HANDLER_NAME, DependenciesSettingsHandler::class.java, project)
                            as DependenciesSettingsHandler)
        }
    }

    private var failOnVersionConflict: Boolean? = null

    /*
     * Returns the root project's extension (if it's been added), or null (if not added, or if this extension is on the
     * root project.
     */
    private val fallback: DependenciesSettingsHandler?
        get() {
            return if (project == project.rootProject) {
                null
            } else {
                findDependenciesSettings(project.rootProject)
            }
        }

   /**
     * If {@code true}, the intrepid plugin will call
     * {@link org.gradle.api.artifacts.ResolutionStrategy#failOnVersionConflict} for each
     * {@link org.gradle.api.artifacts.Configuration} as it is created (unless the "dependencies" or "dependencyInsight"
     * tasks are being run);
     * if unset (null), use the value from the root project (defaulting to true);
     * if false, don't call {@link org.gradle.api.artifacts.ResolutionStrategy#failOnVersionConflict}.
     */
    var defaultFailOnVersionConflict: Boolean
        get() {
            // Fall back to the settings from the root project, if its extension has already been added there.  (It normally
            // will have, but not if the evaluation order is changed with "evaluationDependsOn" or similar.)
            return failOnVersionConflict ?: (fallback?.defaultFailOnVersionConflict ?: DEFAULT_FAIL_ON_VERSION_CONFLICT_DEFAULT)
        }
        set(value) {
            failOnVersionConflict = value
        }
}

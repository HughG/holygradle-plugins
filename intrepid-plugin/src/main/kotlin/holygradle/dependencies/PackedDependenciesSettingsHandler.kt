package holygradle.dependencies

import org.gradle.api.Project
import java.io.File

/**
 * Extension to hold project-wide settings which affect how packed dependencies are handled.
 */
open class PackedDependenciesSettingsHandler(private val project: Project) {
    companion object {
        private const val PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME = "packedDependenciesSettings"
        private const val DEFAULT_UNPACK_CACHE_DIR = "unpackCache"

        @JvmStatic
        fun findPackedDependenciesSettings(project: Project): PackedDependenciesSettingsHandler? {
            return project.extensions.findByName(PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME) as? PackedDependenciesSettingsHandler
        }

        @JvmStatic
        fun findOrCreatePackedDependenciesSettings(project: Project): PackedDependenciesSettingsHandler  {
            return findPackedDependenciesSettings(project) ?:
                    (project.extensions.create(PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME, PackedDependenciesSettingsHandler::class.java, project)
                            as PackedDependenciesSettingsHandler)
        }

        @JvmStatic
        fun getPackedDependenciesSettings(project: Project): PackedDependenciesSettingsHandler {
            return findPackedDependenciesSettings(project)
                    ?: throw RuntimeException("Failed to find ${PACKED_DEPENDENCIES_SETTINGS_HANDLER_NAME} extension on " + project)
        }

    }

    private var _unpackedDependenciesCacheDir: File? = null

    /*
     * Returns the root project's extension (if it's been added), or null (if not added, or if this extension is on the
     * root project.
     */
    private val fallback: PackedDependenciesSettingsHandler?
        get() {
            return if (project == project.rootProject) {
                null
            } else {
                findPackedDependenciesSettings(project.rootProject)
            }
        }

    /**
     * If set, the intrepid plugin will unpack dependencies unto subfolders of the given folder;
     * if unset (null), use the value from the root project (defaulting to "{@code $GRADLE_USER_HOME/unpackCache}").
     */
    var unpackedDependenciesCacheDir: File
        get() {
            // Fall back to the settings from the root project, if its extension has already been added there.  (It normally
            // will have, but not if the evaluation order is changed with "evaluationDependsOn" or similar.)
            return _unpackedDependenciesCacheDir
                    ?: (fallback?.unpackedDependenciesCacheDir ?: File(project.gradle.gradleUserHomeDir, DEFAULT_UNPACK_CACHE_DIR))
        }
        set(value) {
            _unpackedDependenciesCacheDir = value
        }
}

package holygradle.source_dependencies

import holygradle.Helper
import org.gradle.api.*
import holygradle.SettingsFileHelper
import holygradle.dependencies.PackedDependencyOptions
import holygradle.unpacking.PackedDependenciesStateHandler
import holygradle.util.mutableUnique
import org.gradle.api.tasks.TaskAction
import holygradle.kotlin.dsl.getValue
import java.io.File
import java.util.*

open class RecursivelyFetchSourceTask : DefaultTask() {
    companion object {
        private const val NEW_SUBPROJECTS_MESSAGE =
                "Additional subprojects may exist now, and their dependencies might not be satisfied."
    }
    val generateSettingsFileForSubprojects = true
    var recursiveTaskName: String? = null

    init {
        // Prevent people from running fAD from a republishing meta-package.  The createSettingsFile option for packed
        // dependencies will mean that a recursive run of fAD would try to fetch their source dependencies and include
        // them in this multi-project build.  That doesn't make sense in general -- for example, if those source deps
        // were published, how could the intervening packed deps be made to depend on them?  Really the option to include
        // packed deps in the settings file should be removed, when the new republishing / promotion mechanism is done.
        project.gradle.taskGraph.whenReady { graph ->
            if (graph.hasTask(this) &&
                    shouldGenerateSettingsFileForSourceDependencies &&
                    shouldGenerateSettingsFileForPackedDependencies
            ) {
                throw RuntimeException(
                    "Cannot run ${this.name} when the root project both specifies " +
                    "'packedDependenciesDefault.createSettingsFile = true' and has source dependencies. " +
                    "The createSettingsFile option is only for use with republishing, " +
                    "in which case source dependencies are not allowed."
                )
            }
        }
    }

    @TaskAction
    fun checkout() {
        if (shouldGenerateSettingsFileForSourceDependencies) {
            maybeReRun(generateSettingsFileForSourceDependencies)
        } else if (shouldGenerateSettingsFileForPackedDependencies) {
            generateSettingsFileForPackedDependencies()
        }
    }

    val generateSettingsFileForSourceDependencies: Boolean get() {
        return SettingsFileHelper.writeSettingsFileAndDetectChange(project.rootProject)
    }

    private fun maybeReRun(settingsFileChanged: Boolean) {
        val needToReRun =
            (recursiveTaskName != null) && (
                // It may be that on this run we have a different set of source dependencies from the previous run.
                // This can happen if the user edited the source or updated it from source control.  In that case, we
                // will add them to the settings file, and then we need to re-run, in case the new set of subprojects
                // specifies more source or packed dependencies.
                settingsFileChanged ||
                // It may be that we have the same set of source dependencies as on the previous run, but we actually
                // fetched some of them on this run.  That can happen if the target folder for the source dep was
                // deleted between runs.  In that case, we need to re-run because the newly-fetched source might be
                // different from the previous run, so might specify more source or packed dependencies.
                newSourceDependenciesWereFetched
            )
        if (needToReRun) {
            val newFile = File(project.rootDir, "/.restart")
            newFile.createNewFile()
            throw RuntimeException(
                NEW_SUBPROJECTS_MESSAGE + " Now re-running this build due to new source dependencies."
            )
        }
    }

    private fun generateSettingsFileForPackedDependencies() {
        val packedDependenciesState: PackedDependenciesStateHandler by project.extensions
        val allUnpackModules = packedDependenciesState.allUnpackModules
        var pathsForPackedDependencies: MutableCollection<String> = ArrayList(allUnpackModules.size)
        allUnpackModules.values.forEach { modules ->
            modules.forEach { module ->
                module.versions.values.forEach { versionInfo ->
                    if (!versionInfo.hasArtifacts) {
                        // Nothing will have been unpacked/linked, so don't try to include it.
                        logger.info("Not writing settings entry for empty packedDependency ${versionInfo.moduleVersion}")
                        return
                    }
                    val targetPathInWorkspace = versionInfo.targetPathInWorkspace
                    val relativePathInWorkspace =
                            Helper.relativizePath(targetPathInWorkspace, project.rootProject.projectDir)
                    pathsForPackedDependencies.add(relativePathInWorkspace)
                }
            }
        }
        pathsForPackedDependencies = pathsForPackedDependencies.mutableUnique()
        SettingsFileHelper.writeSettingsFile(
            File(project.projectDir, "settings.gradle"),
            pathsForPackedDependencies
        )
    }

    private val shouldGenerateSettingsFileForPackedDependencies: Boolean
        get() {
            val packedDependenciesDefault: PackedDependencyOptions by project.extensions
            return project == project.rootProject && packedDependenciesDefault.shouldCreateSettingsFile
        }

    private val shouldGenerateSettingsFileForSourceDependencies: Boolean
        get() {
            return generateSettingsFileForSubprojects &&
                    !(Helper.getTransitiveSourceDependencies(project.rootProject).isEmpty())
        }

    private val newSourceDependenciesWereFetched: Boolean
        get() {
            return taskDependencies.getDependencies(this).any {
                it is FetchSourceDependencyTask && it.getDidWork()
            }
        }
}

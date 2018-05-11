package holygradle.dependencies

import holygradle.artifacts.ConfigurationHelper
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.AbstractCopyTask
import holygradle.kotlin.dsl.extra
import holygradle.kotlin.dsl.getValue
import java.io.File

class CollectDependenciesHelper(private val copyTask: AbstractCopyTask) {
    companion object {
        val LOCAL_ARTIFACTS_DIR_NAME = "local_artifacts"
        private val CREATE_PUBLISH_NOTES_TASK_NAME = "createPublishNotes"
    }
    private val createPublishNotesTask: Task =
            copyTask.project.tasks.findByName(CREATE_PUBLISH_NOTES_TASK_NAME)
            ?: throw RuntimeException(
                    "${copyTask.name} cannot be configured because ${CREATE_PUBLISH_NOTES_TASK_NAME} was not found in " +
                    "${copyTask.project}"
            )

    /*
     * Tasks configured with this helper can only used on the root project because their purpose is to collect all
     * dependencies for all projects.  For each project we find all dependencies, for both buildscript and project, then
     * find their Ivy/POM files, and copy those metadata files plus all dependency artifacts we need (but not those for
     * unused configurations).  The search doesn't include or look inside dependencies which match sourceDependencies,
     * because they all correspond to sub-projects, and we search through them separately.  We search each project
     * separately because we need all its dependencies in order to build that project from source, but the root project
     * might not have a configuration mapping to all the configurations of each sub-project.
     *
     * In earlier versions the task was applied to and ran separately for each project, with no dependencies between
     * tasks.  Therefore, collecting dependencies for all projects relied on running the task for all projects.  That
     * would happen automatically if you just ran "gw collectDependencies", but if you ran another task which depended
     * on collectDependencies, that dependency would only pull in the task in the root project, which was unexpected and
     * not useful.  So, it was rewritten to run only on the root project, and directly visit all projects.
     *
     * Another approach would have been to leave one task per project and connect them with task dependencies.  However,
     * that would require introducing a new configuration just to establish that cross-project dependencies.  I didn't
     * want to use the "everything" configuration because I want to get rid of it.  Also, doing everything at the root
     * means we can be sure we only copy each dependency file once, even if it's used by multiple subprojects, so it
     * will be faster.
     */
    fun configure(copySpec: CopySpec) {
        val project = copyTask.project
        if (project != project.rootProject) {
            throw RuntimeException("CollectDependenciesTask can only be used in the root project.")
        }

        // Add a dependency on the createPublishNotes task so the build_info folder will be available for copying
        copyTask.dependsOn(createPublishNotesTask)

        // We find the set of artifacts after all projects are evaluated, so that all subprojects and their packed
        // dependencies are known.  We use "this.ext.lazyConfiguration" instead of "project.gradle.projectsEvaluated"
        // because this is slow, so we don't want to do it unless we're really executing the task.
        copyTask.extra["lazyConfiguration"] = {
            val ivyFiles = mutableMapOf<ModuleVersionIdentifier, File>()
            val pomFiles = mutableMapOf<ModuleVersionIdentifier, File>()
            val artifacts = mutableSetOf<ResolvedArtifact>()

            project.allprojects { proj ->
                val buildscriptDependenciesState: DependenciesStateHandler by proj.extensions
                val dependenciesState: DependenciesStateHandler by proj.extensions
                val configurationsWithState = mapOf<ConfigurationContainer, DependenciesStateHandler>(
                    (proj.buildscript.configurations) to buildscriptDependenciesState,
                    (proj.configurations) to dependenciesState
                )
                for ((configurations, state) in configurationsWithState) {
                    for (conf in configurations) {
                        collectFilesFromConfiguration(conf, state, ivyFiles, pomFiles, artifacts)
                    }
                }
            }

            val artifactsWithoutMetadataFiles = mutableSetOf<ResolvedArtifact>()
            configureToCopyFiles(copySpec, ivyFiles, pomFiles, artifacts, artifactsWithoutMetadataFiles)
            if (!artifactsWithoutMetadataFiles.isEmpty()) {
                throw RuntimeException(
                    "Failed to find metadata files for all modules.  Missing ${artifactsWithoutMetadataFiles}"
                )
            }
        }

        configureToCopyPublishNotes(copySpec)
    }

    /**
     * Configures this task to copy all files required to resolve all dependencies for the given configuration.
     * This includes the artifacts, plus all related Ivy and Maven (POM) metadata files.
     *
     * @param conf The configuration for which files are to be copied.
     * @param dependenciesState Helper object for finding dependency metadata files.
     * metadata files could not be found.
     */
    private fun collectFilesFromConfiguration(
            conf: Configuration,
            dependenciesState: DependenciesStateHandler,
            ivyFiles: MutableMap<ModuleVersionIdentifier, File>,
            pomFiles: MutableMap<ModuleVersionIdentifier, File>,
            artifacts: MutableSet<ResolvedArtifact>
    ) {
        ivyFiles.putAll(dependenciesState.getIvyFilesForConfiguration(conf))
        pomFiles.putAll(dependenciesState.getPomFilesForConfiguration(conf))
        pomFiles.putAll(dependenciesState.getAncestorPomFiles(conf))
        collectResolvedArtifacts(conf, dependenciesState, artifacts)
    }

    /**
     * Configures this task to copy all files required to resolve all dependencies for the given configuration.
     * This includes the artifacts, plus all related Ivy and Maven (POM) metadata files.
     *
     * @param artifactsWithoutMetadataFiles Output object, to which are added any artifacts for which the corresponding
     * metadata files could not be found.
     */
    private fun configureToCopyFiles(
            copySpec: CopySpec,
            ivyFiles: MutableMap<ModuleVersionIdentifier, File>,
            pomFiles: MutableMap<ModuleVersionIdentifier, File>,
            artifacts: Set<ResolvedArtifact>,
            artifactsWithoutMetadataFiles: MutableSet<ResolvedArtifact>
    ) {
        configureToCopyIvyFiles(copySpec, ivyFiles)
        configureToCopyPomFiles(copySpec, pomFiles)
        artifacts.filterNotTo(artifactsWithoutMetadataFiles) { configureToCopyArtifact(copySpec, it, ivyFiles, pomFiles) }
    }

    private fun getIvyPath(version: ModuleVersionIdentifier): String =
            "ivy/${version.group}/${version.name}/${version.version}"

    private fun getMavenPath(version: ModuleVersionIdentifier): String =
            "maven/${version.group.replace("\\.".toRegex(), "/")}/${version.name}/${version.version}"

    /**
     * Configures this task to copy the Ivy files for the transitive closure of dependencies of the given configuration.
     * @return A map of files copied.
     */
    private fun configureToCopyIvyFiles(copySpec: CopySpec, ivyFiles: Map<ModuleVersionIdentifier, File>) {
        val getPath = this::getIvyPath
        configureToCopyMetadataFiles(copySpec, ivyFiles, "ivy", getPath)
    }

    /**
     * Configures this task to copy the POM files for the transitive closure of dependencies of the given configuration,
     * plus all the ancestor (parent, parent-of-parent, etc.) POM files.  (Ancestor POM files are not treated as
     * dependencies; rather, they are used to extend the main POM files, possibly defining further dependencies.)
     * @return A map of files copied.
     */
    private fun configureToCopyPomFiles(copySpec: CopySpec, pomFiles: Map<ModuleVersionIdentifier, File>) {
        configureToCopyMetadataFiles(copySpec, pomFiles, "pom", this::getMavenPath)
    }

    private fun configureToCopyMetadataFiles(
            copySpec: CopySpec,
            metadataFiles: Map<ModuleVersionIdentifier, File>,
            metadataFileType: String,
            getPath: (ModuleVersionIdentifier) -> String
    ) {
        for ((version, file) in metadataFiles) {
            val targetPath = getPath(version)
            if (copyTask.logger.isDebugEnabled) {
                copyTask.logger.debug("Copy ${metadataFileType} file from ${file} into ${targetPath}")
            }
            copySpec.from(file) { child ->
                child.into(targetPath)
            }
        }
    }

    /**
     * Returns the complete set of resolved artifacts for a given configuration.
     */
    private fun collectResolvedArtifacts(
        conf: Configuration,
        state: DependenciesStateHandler,
        dependencyArtifacts: MutableSet<ResolvedArtifact>
    ) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        val logger = copyTask.logger
        if (logger.isDebugEnabled) {
            logger.debug("getResolvedArtifacts(${conf}, ...)")
        }
        val firstLevelDeps = ConfigurationHelper.getFirstLevelModuleDependenciesForMaybeOptionalConfiguration(conf)
        firstLevelDeps.forEach { resolvedDependency ->
            if (logger.isDebugEnabled) {
                logger.debug("getResolvedArtifacts: resolvedDependency ${resolvedDependency.module.id}")
            }

            // Only include artifacts for modules which we are not building from source.  Those modules will correspond
            // to other subprojects, and they will be visited separately by the caller of this method.
            if (!state.isModuleInBuild(resolvedDependency.module.id)) {
                resolvedDependency.allModuleArtifacts.forEach { artifact ->
                    if (logger.isDebugEnabled) {
                        logger.debug("getResolvedArtifacts: adding artifact ${artifact.file.name}")
                    }
                    dependencyArtifacts.add(artifact)
                }
            } else {
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "getResolvedArtifacts: skipping subproject module in build ${resolvedDependency.module.id}"
                    )
                }
            }
        }
    }

    /**
     * Configures this task to copy the publish notes into the target "local_artifacts" folder
     */
    private fun configureToCopyPublishNotes(copySpec: CopySpec) {
        val buildInfoDir: File = createPublishNotesTask.extra["buildInfoDir"] as File
        copySpec.from(buildInfoDir.toString()) { child ->
            child.into(buildInfoDir.name)
        }
    }

    /**
     * Configures this task to copy the given artifact into either an Ivy-format or Maven-format file repository
     * sub-folder of the "local_artifacts" folder, depending on whether its module has an "ivx.xml" or ".pom" metadata
     * file.
     * @param artifact The artifact to copy.
     * @param ivyFiles The set of known Ivy files.
     * @param pomFiles The set of known POM files.
     * @return True if the task was configured to copy the artifact; false if not (because no corresponding metadata file
     * was available).
     */
    private fun configureToCopyArtifact(
        copySpec: CopySpec,
        artifact: ResolvedArtifact,
        ivyFiles: Map<ModuleVersionIdentifier, File>,
        pomFiles: Map<ModuleVersionIdentifier, File>
    ): Boolean {
        val version = artifact.moduleVersion.id
        val targetPath: String? = when {
            ivyFiles.containsKey(version) -> getIvyPath(version)
            pomFiles.containsKey(version) -> getMavenPath(version)
            else -> null
        }

        return if (targetPath == null) {
            copyTask.logger.error("Failed to find metadata file corresponding to ${artifact}")
            false
        } else {
            if (copyTask.logger.isDebugEnabled) {
                copyTask.logger.debug("configureToCopyArtifact: ${version} - ${artifact}: copying ${artifact.file} to ${targetPath}")
            }
            copySpec.from (artifact.file) { child ->
                child.into(targetPath)
            }
            true
        }
    }
}
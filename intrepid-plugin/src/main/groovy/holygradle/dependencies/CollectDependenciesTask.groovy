package holygradle.dependencies

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*

class CollectDependenciesTask extends Copy {
    private Project project
    private DependenciesStateHandler buildscriptDependenciesState
    private DependenciesStateHandler dependenciesState

    /*
     * This task is only used on the root project because its purpose is to collect all dependencies for all projects.
     * For each project we find all dependencies, for both buildscript and project, then find their Ivy/POM files, and
     * copy those metadata files plus all dependency artifacts we need (but not those for unused configurations).
     * The search doesn't include or look inside dependencies which match sourceDependencies, because they all
     * correspond to sub-projects, and we search through them separately.  We search each project separately because we
     * need all its dependencies in order to build that project from source, but the root project might not have a
     * configuration mapping to all the configurations of each sub-project.
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
    public void initialize(Project project) {
        if (project != project.rootProject) {
            throw new RuntimeException("CollectDependenciesTask can only be used in the root project.")
        }

        this.project = project
        this.buildscriptDependenciesState = project.extensions.buildscriptDependenciesState
        this.dependenciesState = project.extensions.dependenciesState

        // Add a dependency on the createPublishNotes task so the build_info folder will be available for copying
        Task createPublishNotesTask = project.tasks.findByName("createPublishNotes")
        if (createPublishNotesTask != null) {
            this.dependsOn createPublishNotesTask
        }

        destinationDir = new File(project.rootProject.projectDir, "local_artifacts")
        doFirst {
            if (destinationDir.exists()) {
                throw new RuntimeException(
                    "Cannot run CollectDependenciesTask when ${destinationDir} already exists, " +
                    "because it would overwrite files for the running version of the holygradle plugins."
                )
            }
        }

        // Take local references to private members for use inside closure
        final def localBuildscriptDeps = this.buildscriptDependenciesState
        final def localDeps = this.dependenciesState
        // We find the set of artifacts after all projects are evaluated, so that all subprojects and their packed
        // dependencies are known.  We use "this.ext.lazyConfiguration" instead of "project.gradle.projectsEvaluated"
        // because this is slow, so we don't want to do it unless we're really executing the task.
        this.ext.lazyConfiguration = {

            Map<ModuleVersionIdentifier, File> ivyFiles = new HashMap<ModuleVersionIdentifier, File>()
            Map<ModuleVersionIdentifier, File> pomFiles = new HashMap<ModuleVersionIdentifier, File>()
            Set<ResolvedArtifact> artifacts = new HashSet<ResolvedArtifact>()

            project.allprojects { Project proj ->
                final Map<ConfigurationContainer, DependenciesStateHandler> configurationsWithState = [
                    (proj.buildscript.configurations): localBuildscriptDeps,
                    (proj.configurations): localDeps
                ]
                configurationsWithState.each {
                    ConfigurationContainer configurations, DependenciesStateHandler dependenciesState
                        ->
                    configurations.each((Closure){ Configuration conf ->
                        collectFilesFromConfiguration(conf, dependenciesState, ivyFiles, pomFiles, artifacts)
                    })
                }
            }

            Set<ResolvedArtifact> artifactsWithoutMetadataFiles = new HashSet<ResolvedArtifact>()
            configureToCopyFiles(ivyFiles, pomFiles, artifacts, artifactsWithoutMetadataFiles)
            if (!artifactsWithoutMetadataFiles.isEmpty()) {
                throw new RuntimeException(
                    "Failed to find metadata files for all modules.  Missing ${artifactsWithoutMetadataFiles}"
                )
            }
        }

        configureToCopyCustomGradleDistribution()
        configureToCopyPublishNotes()
        configureToRewriteGradleWrapperProperties()
    }

    /**
     * Configures this task to copy all files required to resolve all dependencies for the given configuration.
     * This includes the artifacts, plus all related Ivy and Maven (POM) metadata files.
     *
     * @param conf The configuration for which files are to be copied.
     * @param dependenciesState Helper object for finding dependency metadata files.
     * @param artifactsWithoutMetadataFiles Output object, to which are added any artifacts for which the corresponding
     * metadata files could not be found.
     */
    public void collectFilesFromConfiguration(
        Configuration conf,
        DependenciesStateHandler dependenciesState,
        Map<ModuleVersionIdentifier, File> ivyFiles,
        Map<ModuleVersionIdentifier, File> pomFiles,
        Set<ResolvedArtifact> artifacts
    ) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        ivyFiles << dependenciesState.getIvyFilesForConfiguration(conf)
        pomFiles << dependenciesState.getPomFilesForConfiguration(conf)
        pomFiles << dependenciesState.getAncestorPomFiles(conf)
        collectResolvedArtifacts(conf, dependenciesState, artifacts)
    }

    /**
     * Configures this task to copy all files required to resolve all dependencies for the given configuration.
     * This includes the artifacts, plus all related Ivy and Maven (POM) metadata files.
     *
     * @param conf The configuration for which files are to be copied.
     * @param dependenciesState Helper object for finding dependency metadata files.
     * @param artifactsWithoutMetadataFiles Output object, to which are added any artifacts for which the corresponding
     * metadata files could not be found.
     */
    public void configureToCopyFiles(
        Map<ModuleVersionIdentifier, File> ivyFiles,
        Map<ModuleVersionIdentifier, File> pomFiles,
        Set<ResolvedArtifact> artifacts,
        Set<ResolvedArtifact> artifactsWithoutMetadataFiles
    ) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        configureToCopyIvyFiles(ivyFiles)
        configureToCopyPomFiles(pomFiles)
        for (ResolvedArtifact artifact in artifacts) {
            if (!configureToCopyArtifact(artifact, ivyFiles, pomFiles)) {
                artifactsWithoutMetadataFiles.add(artifact)
            }
        }
    }

    private static String getIvyPath(ModuleVersionIdentifier version) {
        "ivy/${version.group}/${version.name}/${version.version}"
    }

    private static String getMavenPath(ModuleVersionIdentifier version) {
        "maven/${version.group.replaceAll(/\./, '/')}/${version.name}/${version.version}"
    }

    /**
     * Configures this task to copy the Ivy files for the transitive closure of dependencies of the given configuration.
     * @return A map of files copied.
     */
    public void configureToCopyIvyFiles(Map<ModuleVersionIdentifier, File> ivyFiles) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        ivyFiles.each { ModuleVersionIdentifier version, File ivyFile ->
            String targetPath = getIvyPath(version)
            if (logger.isDebugEnabled()) {
                logger.debug("Copy ivyFile from ${ivyFile} into ${targetPath}")
            }
            from(ivyFile) {
                into targetPath
            }
        }
    }

    /**
     * Configures this task to copy the POM files for the transitive closure of dependencies of the given configuration,
     * plus all the ancestor (parent, parent-of-parent, etc.) POM files.  (Ancestor POM files are not treated as
     * dependencies; rather, they are used to extend the main POM files, possibly defining further dependencies.)
     * @return A map of files copied.
     */
    public void configureToCopyPomFiles(Map<ModuleVersionIdentifier, File> pomFiles) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        pomFiles.each { ModuleVersionIdentifier version, File ivyFile ->
            String targetPath = getMavenPath(version)
            if (logger.isDebugEnabled()) {
                logger.debug("Copy pomFile from ${ivyFile} into ${targetPath}")
            }
            from(ivyFile) {
                into targetPath
            }
        }
    }

    /**
     * Returns the complete set of resolved artifacts for a given configuration.
     */
    public void collectResolvedArtifacts(
        Configuration conf,
        DependenciesStateHandler state,
        Set<ResolvedArtifact> dependencyArtifacts
    ) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        if (logger.isDebugEnabled()) {
            logger.debug("getResolvedArtifacts(${conf}, ...)")
        }
        ResolvedConfiguration resConf = conf.resolvedConfiguration
        resConf.firstLevelModuleDependencies.each { ResolvedDependency resolvedDependency ->
            if (logger.isDebugEnabled()) {
                logger.debug("getResolvedArtifacts: resolvedDependency ${resolvedDependency.module.id}")
            }

            // Only include artifacts for modules which we are not building from source.  Those modules will correspond
            // to other subprojects, and they will be visited separately by the caller of this method.
            if (!state.isModuleInBuild(resolvedDependency.module.id)) {
                resolvedDependency.allModuleArtifacts.each { ResolvedArtifact artifact ->
                    if (logger.isDebugEnabled()) {
                        logger.debug("getResolvedArtifacts: adding artifact ${artifact.file.name}")
                    }
                    dependencyArtifacts.add(artifact)
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "getResolvedArtifacts: skipping subprojcet module in build ${resolvedDependency.module.id}"
                    )
                }
            }
        }
    }

    /**
     * Configures this task to copy the custom-gradle distribution into the target "local_artifacts" folder.
     */
    private void configureToCopyCustomGradleDistribution() {
        // project.gradle.gradleHomeDir will be something like:
        //
        //   C:\Users\nmcm\.gradle\wrapper\dists\custom-gradle-1.3-1.0.1.10\j5dmdk875e2rad4dnud3sriop\custom-gradle-1.3
        //
        // We want to copy the ZIP file in
        //
        //   C:\Users\nmcm\.gradle\wrapper\dists\custom-gradle-1.3-1.0.1.10\j5dmdk875e2rad4dnud3sriop
        //
        // called
        //
        //   custom-gradle-1.3-1.0.1.10.zip
        //
        // to
        //
        //   custom-gradle/1.0.1.10

        final String distName = project.gradle.gradleHomeDir.parentFile.parentFile.name
        final String[] splitDistName = distName.split("-")
        String customDistVersion = splitDistName[-1]
        from(new File(project.gradle.gradleHomeDir.parentFile, distName + ".zip").toString()) {
            into "custom-gradle/${customDistVersion}"
        }
    }

    /**
     * Configures this task to copy the publish notes into the target "local_artifacts" folder
     */
    private void configureToCopyPublishNotes() {
        Task createPublishNotesTask = project.tasks.findByName("createPublishNotes")
        if (createPublishNotesTask != null) {
            from(createPublishNotesTask.ext.buildInfoDir.toString()) {
                into createPublishNotesTask.ext.buildInfoDir.name
            }
        }
    }

    /**
     * Configures this task to rewrite the "gradle/gradle-wrapper.properties" so that it will get the custom-gradle
     * distribution from the "local_artifacts" folder.
     */
    private void configureToRewriteGradleWrapperProperties() {
        doLast {
            File gradleDir = new File(project.projectDir, "gradle")
            File propFile = new File(gradleDir, "gradle-wrapper.properties")

            if (!propFile.exists()) {
                throw new RuntimeException("Cannot modify ${propFile} because it does not exist")
            }

            String originalPropText = propFile.text
            File backupPropFile = new File(propFile.path + ".original")
            backupPropFile.write(originalPropText)

            String newPropText = originalPropText.replaceAll(
                "distributionUrl=.*custom-gradle/([\\d\\.]+)/custom-gradle-([\\d\\.\\-]+).zip",
                { "distributionUrl=../local_artifacts/custom-gradle/${it[1]}/custom-gradle-${it[2]}.zip" }
            )
            propFile.write(newPropText)

            logger.info(
                "NOTE: '${propFile.name}' has been rewritten to refer to the 'local_artifacts' version of " +
                "custom-gradle. A backup has been saved as '${backupPropFile.name}'."
            )
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
    public boolean configureToCopyArtifact(
        ResolvedArtifact artifact,
        Map<ModuleVersionIdentifier, File> ivyFiles,
        Map<ModuleVersionIdentifier, File> pomFiles
    ) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        ModuleVersionIdentifier version = artifact.moduleVersion.id
        String targetPath = null

        if (ivyFiles.containsKey(version)) {
            targetPath = getIvyPath(version)
        } else if (pomFiles.containsKey(version)) {
            targetPath = getMavenPath(version)
        }

        final boolean foundMetadataFile = (targetPath != null)
        if (!foundMetadataFile) {
            logger.error("Failed to find metadata file corresponding to ${artifact}")
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("configureToCopyArtifact: ${version} - ${artifact}: copying ${artifact.file} to ${targetPath}")
            }
            from (artifact.getFile()) {
                into targetPath
            }
        }
        return foundMetadataFile
    }
}
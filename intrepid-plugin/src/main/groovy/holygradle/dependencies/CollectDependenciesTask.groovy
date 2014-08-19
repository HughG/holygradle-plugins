package holygradle.dependencies

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*

class CollectDependenciesTask extends Copy {
    private Project project
    private DependenciesStateHandler buildscriptDependenciesState
    private DependenciesStateHandler dependenciesState

    public void initialize(Project project) {
        this.project = project
        this.buildscriptDependenciesState = project.extensions.buildscriptDependenciesState
        this.dependenciesState = project.extensions.dependenciesState

        destinationDir = new File(project.rootProject.projectDir, "local_artifacts")
        // Take local references to private members for use inside closure
        final def localBuildscriptDeps = this.buildscriptDependenciesState
        final def localDeps = this.dependenciesState
        this.ext.lazyConfiguration = {

            Set<ResolvedArtifact> artifactsWithoutMetadataFiles = new HashSet<ResolvedArtifact>()
            final Map<ConfigurationContainer, DependenciesStateHandler> configurationsWithState = [
                (project.buildscript.configurations): localBuildscriptDeps,
                (project.configurations): localDeps
            ]
            configurationsWithState.each {
                ConfigurationContainer configurations, DependenciesStateHandler dependenciesState
             ->
                configurations.each((Closure){ Configuration conf ->
                    configureToCopyModulesFromConfiguration(conf, dependenciesState, artifactsWithoutMetadataFiles)
                })
            }

            if (!artifactsWithoutMetadataFiles.isEmpty()) {
                throw new RuntimeException(
                    "Failed to find metadata files for all modules.  Missing ${artifactsWithoutMetadataFiles}"
                )
            }
        }

        if (project == project.rootProject) {
            configureToCopyCustomGradleDistribution()
            configureToRewriteGradleWrapperProperties()
        }
    }

    /**
     * Configures this task to copy all files required to resolve all dependencies for the given configuration.
     * This includes the artifacts, plus all related Ivy and Maven (POM) metaadta files.
     *
     * @param conf The configuration for which files are to be copied.
     * @param dependenciesState Helper object for finding dependency metadata files.
     * @param artifactsWithoutMetadataFiles Output object, to which are added any artifacts for which the corresponding
     * metadata files could not be found.
     */
    public void configureToCopyModulesFromConfiguration(
        Configuration conf,
        DependenciesStateHandler dependenciesState,
        Set<ResolvedArtifact> artifactsWithoutMetadataFiles
    ) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        Map<ModuleVersionIdentifier, File> ivyFiles = configureToCopyIvyFiles(conf, dependenciesState)
        Map<ModuleVersionIdentifier, File> pomFiles = configureToCopyPomFiles(conf, dependenciesState)
        for (ResolvedArtifact artifact in getResolvedArtifacts(conf, dependenciesState)) {
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
    public Map<ModuleVersionIdentifier, File> configureToCopyIvyFiles(Configuration conf, DependenciesStateHandler state) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        Map<ModuleVersionIdentifier, File> ivyFiles = state.getIvyFilesForConfiguration(conf)
        ivyFiles.each { ModuleVersionIdentifier version, File ivyFile ->
            String targetPath = getIvyPath(version)
            println "Copy ivyFile from ${ivyFile} into ${targetPath}"
            from(ivyFile) {
                into targetPath
            }
        }
        return ivyFiles
    }

    /**
     * Configures this task to copy the POM files for the transitive closure of dependencies of the given configuration,
     * plus all the ancestor (parent, parent-of-parent, etc.) POM files.  (Ancestor POM files are not treated as
     * dependencies; rather, they are used to extend the main POM files, possibly defining further dependencies.)
     * @return A map of files copied.
     */
    public Map<ModuleVersionIdentifier, File> configureToCopyPomFiles(Configuration conf, DependenciesStateHandler state) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

//        TODO 2014-07-14 HughG: I should keep a set of artiacts I've already asked to copy, so I don't ask more than once.


        Map<ModuleVersionIdentifier, File> pomFilesWithAncestors = new HashMap<ModuleVersionIdentifier, File>()

        Collection<Map<ModuleVersionIdentifier, File>> pomFileMaps = [
            state.getPomFilesForConfiguration(conf),
            state.getAncestorPomFiles(conf)
        ]
        pomFileMaps.each { Map<ModuleVersionIdentifier, File> pomFiles ->
            pomFiles.each { ModuleVersionIdentifier version, File ivyFile ->
                String targetPath = getMavenPath(version)
                println "Copy pomFile from ${ivyFile} into ${targetPath}"
                from(ivyFile) {
                    into targetPath
                }
            }

            pomFilesWithAncestors.putAll(pomFiles)
        }

        return pomFilesWithAncestors
    }

    /**
     * Returns the complete set of resolved artifacts for a given configuration.
     */
    public Set<ResolvedArtifact> getResolvedArtifacts(Configuration conf, DependenciesStateHandler state) {
        // Only public because of stupid Gradle 1.4 "feature" that private members aren't visible to closures.
        // Taking a local copy ends in a "no applicable method" error for some unknown reason.

        Set<ResolvedArtifact> dependencyArtifacts = new HashSet<ResolvedArtifact>()
        ResolvedConfiguration resConf = conf.resolvedConfiguration
        resConf.firstLevelModuleDependencies.each { ResolvedDependency resolvedDependency ->
            // Only include artifacts for modules which we are not building from source.
            if (!state.isModuleInBuild(resolvedDependency.module.id)) {
                resolvedDependency.allModuleArtifacts.each { ResolvedArtifact artifact ->
                    dependencyArtifacts.add(artifact)
                }
            }
        }
        return dependencyArtifacts
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

            project.logger.info(
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
            logger.debug("configureToCopyArtifact: ${version} - ${artifact}: copying ${artifact.file} to ${targetPath}")
            from (artifact.getFile()) {
                into targetPath
            }
        }
        return foundMetadataFile
    }
}
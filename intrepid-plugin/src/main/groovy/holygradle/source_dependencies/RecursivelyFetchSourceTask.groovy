package holygradle.source_dependencies

import holygradle.Helper
import holygradle.dependencies.PackedDependencyHandler
import holygradle.unpacking.UnpackModule
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.*
import holygradle.SettingsFileHelper
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.TaskAction

class RecursivelyFetchSourceTask extends DefaultTask {
    public static final String NEW_SUBPROJECTS_MESSAGE =
        "Additional subprojects may exist now, and their dependencies might not be satisfied."
    public boolean generateSettingsFileForSubprojects = true
    public String recursiveTaskName = null

    RecursivelyFetchSourceTask() {
        // Prevent people from running fAD from a republishing meta-package.  The createSettingsFile option for packed
        // dependencies will mean that a recursive run of fAD would try to fetch their source dependencies and include
        // them in this multi-project build.  That doesn't make sense in general -- for example, if those source deps
        // were published, how could the intervening packed deps be made to depend on them?  Really the option to include
        // packed deps in the settings file should be removed, when the new republishing / promotion mechanism is done.
        project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
            if (graph.hasTask(this) &&
                shouldGenerateSettingsFileForSourceDependencies() &&
                shouldGenerateSettingsFileForPackedDependencies()
            ) {
                throw new RuntimeException(
                    "Cannot run ${this.name} when the root project both specifies " +
                    "'packedDependenciesDefault.createSettingsFile = true' and has source dependencies. " +
                    "The createSettingsFile option is only for use with republishing, " +
                    "in which case source dependencies are not allowed."
                )
            }
        }
    }

    @TaskAction
    public void Checkout() {
        if (shouldGenerateSettingsFileForSourceDependencies()) {
            maybeReRun(generateSettingsFileForSourceDependencies())
        } else if (shouldGenerateSettingsFileForPackedDependencies()) {
            generateSettingsFileForPackedDependencies()
        }
    }

    private boolean generateSettingsFileForSourceDependencies() {
        return SettingsFileHelper.writeSettingsFileAndDetectChange(project.rootProject)
    }

    private void maybeReRun(boolean settingsFileChanged) {
        boolean needToReRun =
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
            File newFile = new File(project.rootDir, "/.restart")
            newFile.createNewFile()
            throw new RuntimeException(
                    NEW_SUBPROJECTS_MESSAGE + " Now re-running this build due to new source dependencies."
            )
        }
    }

    private void generateSettingsFileForPackedDependencies() {
        final Collection<UnpackModule> allUnpackModules = project.extensions.packedDependenciesState.allUnpackModules
        Collection<String> pathsForPackedDependencies = new ArrayList<String>(allUnpackModules.size())
        allUnpackModules.each { UnpackModule module ->
            module.versions.values().each { UnpackModuleVersion versionInfo ->
                if (!versionInfo.hasArtifacts()) {
                    // Nothing will have been unpacked/linked, so don't try to include it.
                    logger.info("Not writing settings entry for empty packedDependency ${versionInfo.moduleVersion}")
                    return
                }
                final File targetPathInWorkspace = versionInfo.targetPathInWorkspace
                final relativePathInWorkspace =
                    Helper.relativizePath(targetPathInWorkspace, project.rootProject.projectDir)
                pathsForPackedDependencies.add(relativePathInWorkspace)
            }
        }
        pathsForPackedDependencies = pathsForPackedDependencies.unique()
        SettingsFileHelper.writeSettingsFile(
            new File(project.projectDir, "settings.gradle"),
            pathsForPackedDependencies
        )
    }

    public boolean shouldGenerateSettingsFileForPackedDependencies() {
        final PackedDependencyHandler packedDependenciesDefault =
            project.packedDependenciesDefault as PackedDependencyHandler
        return project == project.rootProject && packedDependenciesDefault.shouldCreateSettingsFile()
    }

    public boolean shouldGenerateSettingsFileForSourceDependencies() {
        return generateSettingsFileForSubprojects &&
            !(Helper.getTransitiveSourceDependencies(project.rootProject).empty)
    }

    public boolean isNewSourceDependenciesWereFetched() {
        return taskDependencies.getDependencies(this).any {
            it instanceof FetchSourceDependencyTask && it.getDidWork()
        }
    }

}

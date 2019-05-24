package holygradle.dependencies

import holygradle.custom_gradle.BuildHelper
import holygradle.kotlin.dsl.getValue
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

internal class SourceOverridesDependencyResolutionListener(
        private val project: Project
) : DependencyResolutionListener, BuildListener {

    private var beforeResolveException : Exception? = null
    private val unusedSourceOverrides by lazy {
        val sourceOverrides: NamedDomainObjectContainer<SourceOverrideHandler> by project.extensions
        sourceOverrides.mapTo(mutableSetOf()) { it.dependencyCoordinate }
    }

    override fun beforeResolve(dependencies: ResolvableDependencies) {
        if (findProjectConfiguration(dependencies) == null) {
            project.logger.debug("source overrides: beforeResolve SKIPPING ${dependencies.path}")
            return // because source overrides aren't applicable to the buildscript or to detached configurations
        }
        project.logger.debug("source overrides: beforeResolve ${dependencies.path}")

        val sourceOverrides: NamedDomainObjectContainer<SourceOverrideHandler> by project.extensions

        // Create dummy module files for each source override.  (Internally it will only do this once per build
        // run.)  We do this in beforeResolve handler because we don't need to do it unless we are actually
        // resolving some configurations: for tasks like "tasks" we don't need to.
        try {
            // First, check that all handlers are valid.  Otherwise we may waste time generating files for
            // some, only for the others to fail.
            sourceOverrides.forEach { handler -> handler.checkValid() }
            // Now actually generate the files.
            sourceOverrides.forEach { handler ->
                project.logger.debug(
                    "beforeResolve ${dependencies.name}; " +
                    "generating dummy module files for ${handler.dependencyCoordinate}"
                )
                handler.generateDummyModuleFiles()
            }
        } catch (e: Exception) {
            // Throwing in the beforeResolve handler leaves logging in a broken state so we need to catch
            // this exception and throw it later.
            beforeResolveException = e
        }
    }

    override fun afterResolve(dependencies: ResolvableDependencies) {
        val projectConfiguration = findProjectConfiguration(dependencies)
        if (projectConfiguration == null) {
            project.logger.debug("source overrides: afterResolve SKIPPING ${dependencies.path}")
            return // because source overrides aren't applicable to the buildscript or to detached configurations
        }

        project.logger.debug("source overrides: afterResolve ${dependencies.path}")
        rethrowIfBeforeResolveHadException()
        noteUsedSourceOverrides(dependencies)
        debugLogResolvedDependencies(dependencies)
    }

    override fun buildStarted(gradle: Gradle) { }

    override fun settingsEvaluated(settings: Settings) { }

    override fun projectsLoaded(gradle: Gradle) { }

    override fun projectsEvaluated(gradle: Gradle) { }

    override fun buildFinished(result: BuildResult) {
        if (!unusedSourceOverrides.isEmpty()) {
            project.logger.warn(
                "Some source override definitions in ${project} did not match any of the direct or transitive " +
                "dependencies of any configuration.  Please check that these source override dependency coordinates " +
                "are correct: " + unusedSourceOverrides.joinToString()
            )
        }
        val sourceOverrides: NamedDomainObjectContainer<SourceOverrideHandler> by project.extensions
        if (!sourceOverrides.isEmpty() && BuildHelper.buildFailedDueToVersionConflict(result)) {
//2345678901234567890123456789012345678901234567890123456789012345678901234567890 <-- 80-column ruler
            project.logger.lifecycle(
                """
This project has source overrides, which can cause module version conflicts for
more complicated reasons.  The source override mechanism copies dependency
information from override projects and adds them as direct dependencies of the
overridden module version.  So, when you run the 'dependencies' task to display
dependency trees, any version conflicts ("group:name:version1 -> version2") may
come from the source override projects.

There are four possible causes of version conflicts when a project has source
overrides.

1. The conflict is not related to source overrides and is just caused by normal
conflicts between versions of indirect dependencies.  You can resolve these as
normal.

2. The root build (the root project and/or its subprojects) has versions which
conflict with one or more source override builds (the root project of a source
override and/or its subprojects).  You can detect this because one of the
conflicting versions will appear in the tree under a module with an override
version ("SOURCE_..."), and the other will appear not under any override.  
You can resolve this by changing the dependency version in the root build, or
in the override build.  In the latter case it may be helpful to run the
'dependencies' task separately in each conflicting source override directory.

3. Similar to the previous case, two or more different source overrides
builds (the root project of a source override and/or its subprojects) may have
versions which conflict with each other, even though they don't conflict with
dependencies from the root project.  You can detect this because each
conflicting version will appear under a module with an override version
("SOURCE_...").  Again, to resolve this, change the dependency version in one
or more override builds.  It may be useful to run the 'dependencies' task
separately in each source override directory.  

4. Two or more source overrides may have been added for the same original
module version ("group:name:version") but pointing to different locations on
disk.  All overrides for the same original version must point to the same
location.  (They may be specified as different relative paths but they must
resolve to the same absolute location.)  You can detect this because the two
versions involved in the conflict are both source override versions
("SOURCE_...").  To resolve this, change all overrides for the same original
module version to point to the same location.

Remember that override projects can also contain further source overrides, so
you may have to run the 'dependencies' task at multiple levels.
"""
//2345678901234567890123456789012345678901234567890123456789012345678901234567890 <-- 80-column ruler
            )
        }
    }

    private fun findProjectConfiguration(dependencies: ResolvableDependencies): Configuration? {
        val depsPath = dependencies.path
        return if (depsPath.startsWith(':')) {
            project.configurations.findByName(dependencies.name)
        } else {
            // This is a buildscript configuration, or maybe a copy or detached configuration.
            null
        }
    }

    private fun rethrowIfBeforeResolveHadException() {
        if (beforeResolveException != null) {
            // This exception is stored from earlier and thrown here.  Usually this is too deeply nested for
            // Gradle to output useful information at the default logging level, so we also explicitly log the
            // the nested exception.
            val message = "beforeResolve handler failed"
            project.logger.error("${message}: ${beforeResolveException.toString()}", beforeResolveException)
            throw RuntimeException(message, beforeResolveException)
        }
    }

    private fun noteUsedSourceOverrides(dependencies: ResolvableDependencies) {
        dependencies.resolutionResult.allDependencies.forEach { result ->
            if (result is ResolvedDependencyResult) {
                unusedSourceOverrides.remove(result.selected.id.toString())
            }
        }
    }

    private fun debugLogResolvedDependencies(dependencies: ResolvableDependencies) {
        if (project.logger.isDebugEnabled) {
            dependencies.resolutionResult.allDependencies.forEach { result ->
                if (result is ResolvedDependencyResult) {
                    project.logger.debug(
                        "  resolved ${result.requested} to ${result.selected}"
                    )
                } else {
                    project.logger.debug(
                        "  UNRESOLVED ${result.requested}: ${result}"
                    )
                }
            }
        }
    }
}

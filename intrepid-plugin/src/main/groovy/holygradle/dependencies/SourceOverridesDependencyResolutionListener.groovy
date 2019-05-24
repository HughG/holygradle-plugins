package holygradle.dependencies

import holygradle.custom_gradle.BuildHelper
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

class SourceOverridesDependencyResolutionListener implements DependencyResolutionListener, BuildListener {
    private final Project project

    public SourceOverridesDependencyResolutionListener(Project project) {
        this.project = project
    }

    private Exception beforeResolveException = null
    private final Object lazyInitLock = new Object()
    private final Set<String> unusedSourceOverrides = new HashSet<>()

    @Override
    void beforeResolve(ResolvableDependencies resolvableDependencies) {
        if (findProjectConfiguration(resolvableDependencies) == null) {
            project.logger.debug("source overrides: beforeResolve SKIPPING ${resolvableDependencies.path}")
            return // because source overrides aren't applicable to the buildscript or to detached configurations
        }
        project.logger.debug("source overrides: beforeResolve ${resolvableDependencies.path}")

        NamedDomainObjectContainer<SourceOverrideHandler> sourceOverrides = project.sourceOverrides

        synchronized (lazyInitLock) {
            if (unusedSourceOverrides.empty) {
                unusedSourceOverrides.addAll(project.sourceOverrides*.dependencyCoordinate)
            }
        }

        // Create dummy module files for each source override.  (Internally it will only do this once per build
        // run.)  We do this in beforeResolve handler because we don't need to do it unless we are actually
        // resolving some configurations: for tasks like "tasks" we don't need to.
        try {
            // First, check that all handlers are valid.  Otherwise we may waste time generating files for
            // some, only for the others to fail.
            sourceOverrides.each { SourceOverrideHandler handler -> handler.checkValid() }
            // Now actually generate the files.
            sourceOverrides.each { SourceOverrideHandler handler ->
                project.logger.debug(
                    "beforeResolve ${resolvableDependencies.name}; " +
                    "generating dummy module files for ${handler.dependencyCoordinate}"
                )
                handler.generateDummyModuleFiles()
            }
        } catch (Exception e) {
            // Throwing in the beforeResolve handler leaves logging in a broken state so we need to catch
            // this exception and throw it later.
            beforeResolveException = e
        }
    }

    @Override
    void afterResolve(ResolvableDependencies resolvableDependencies) {
        final Configuration projectConfiguration = findProjectConfiguration(resolvableDependencies)
        if (projectConfiguration == null) {
            project.logger.debug("source overrides: afterResolve SKIPPING ${resolvableDependencies.path}")
            return // because source overrides aren't applicable to the buildscript or to detached configurations
        }

        project.logger.debug("source overrides: afterResolve ${resolvableDependencies.path}")
        rethrowIfBeforeResolveHadException()
        noteUsedSourceOverrides(resolvableDependencies)
        debugLogResolvedDependencies(resolvableDependencies)
    }

    @Override void buildStarted(Gradle gradle) { }

    @Override void settingsEvaluated(Settings settings) { }

    @Override void projectsLoaded(Gradle gradle) { }

    @Override void projectsEvaluated(Gradle gradle) { }

    @Override
    void buildFinished(BuildResult buildResult) {
        if (!unusedSourceOverrides.empty) {
            project.logger.warn(
                "Some source override definitions in ${project} did not match any of the direct or transitive " +
                "dependencies of any configuration.  Please check that these source override dependency coordinates " +

                "are correct: " + unusedSourceOverrides.join(",")
            )
        }
        if (!project.sourceOverrides.empty && BuildHelper.buildFailedDueToVersionConflict(buildResult)) {
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

    private Configuration findProjectConfiguration(ResolvableDependencies resolvableDependencies) {
        final String depsPath = resolvableDependencies.path
        if (depsPath.startsWith(':')) {
            return project.configurations.findByName(resolvableDependencies.name)
        } else {
            // This is a buildscript configuration, or maybe a copy or detached configuration.
            return null
        }
    }

    private void rethrowIfBeforeResolveHadException() {
        if (beforeResolveException != null) {
            // This exception is stored from earlier and thrown here.  Usually this is too deeply nested for
            // Gradle to output useful information at the default logging level, so we also explicitly log the
            // the nested exception.
            final String message = "beforeResolve handler failed"
            project.logger.error("${message}: ${beforeResolveException.toString()}", beforeResolveException)
            throw new RuntimeException(message, beforeResolveException)
        }
    }

    private void noteUsedSourceOverrides(ResolvableDependencies resolvableDependencies) {
        resolvableDependencies.resolutionResult.allDependencies.each { DependencyResult result ->
            if (result instanceof ResolvedDependencyResult) {
                unusedSourceOverrides.remove(((ResolvedDependencyResult) result).selected.id.toString())
            }
        }
    }

    private void debugLogResolvedDependencies(ResolvableDependencies resolvableDependencies) {
        if (project.logger.isDebugEnabled()) {
            resolvableDependencies.resolutionResult.allDependencies.each { DependencyResult result ->
                if (result instanceof ResolvedDependencyResult) {
                    project.logger.debug(
                        "  resolved ${result.requested} to ${((ResolvedDependencyResult) result).selected}"
                    )
                } else {
                    project.logger.debug(
                        "  UNRESOLVED ${result.requested}: ${(UnresolvedDependencyResult) result}"
                    )
                }
            }
        }
    }
}

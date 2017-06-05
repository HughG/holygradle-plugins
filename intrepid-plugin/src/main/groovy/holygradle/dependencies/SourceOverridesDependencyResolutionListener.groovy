package holygradle.dependencies

import groovy.util.slurpersupport.GPathResult
import holygradle.Helper
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution
import org.gradle.api.invocation.Gradle

class SourceOverridesDependencyResolutionListener implements DependencyResolutionListener, BuildListener {
    private final Project project

    public SourceOverridesDependencyResolutionListener(Project project) {
        this.project = project
    }

    private Exception beforeResolveException = null
    // Mapping each handler
    //   to each configuration in the override project
    //     to each "group:name" dependency in that configuration (including all transitive deps, not just
    // direct)
    //       to the "version" for that dependency (which might be a "normal" version or also an override).
    //
    // We use LinkedHashMaps to get a stable order for which things appear first in the error messages.
    private LinkedHashMap<
        SourceOverrideHandler, LinkedHashMap<String, LinkedHashMap<String,String>>
        > sourceOverrideDependencies = null

    // TODO 2016-04-29 HUGR: We should extend source overrides to allow different overrides per configuration,
    // but currently we don't.  So, we only need to calculate this once, which is helpful for the common case
    // where there are no conflicts.  (Even if we did it per configuration, we might be able to be smart about
    // not re-doing work if we considered extendsFrom relationships.)  In that case, we probably need to support
    // adding overrides in subprojects, because they might need to apply to configurations which are not
    // connected to any in the root project.
    private List<String> conflictBetweenOverrideMessages = null

    private Set<String> unusedSourceOverrides = new HashSet<>()

    @Override
    void beforeResolve(ResolvableDependencies resolvableDependencies) {
        if (findProjectConfiguration(resolvableDependencies) == null) {
            project.logger.debug("source overrides: beforeResolve SKIPPING ${resolvableDependencies.path}")
            return // because source overrides aren't applicable to the buildscript or to detached configurations
        }
        project.logger.debug("source overrides: beforeResolve ${resolvableDependencies.path}")

        NamedDomainObjectContainer<SourceOverrideHandler> sourceOverrides = project.sourceOverrides

        if (unusedSourceOverrides.empty) {
            unusedSourceOverrides.addAll(project.sourceOverrides*.dependencyCoordinate)
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
        Map<SourceOverrideHandler, Map<String, Map<String, String>>> sourceOverrideDependencies =
            getSourceOverrideDependencies()

        List<String> conflictMessages = new LinkedList<String>()

        sourceOverrideDependencies.each { handler, deps ->
            // Compare source override full dependencies to the current dependency
            collectConflictsForProjectVsOverride(conflictMessages, resolvableDependencies, handler, deps)
        }

        // Compare source override full dependencies to each other
        collectConflictsBetweenOverrides(resolvableDependencies.path, conflictMessages, sourceOverrideDependencies)

        logOrThrowIfConflicts(conflictMessages, projectConfiguration)
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

    private Map<SourceOverrideHandler, Map<String, Map<String, String>>> getSourceOverrideDependencies() {
        if (sourceOverrideDependencies == null) {
            readSourceOverrideDependencies()
        }
        return sourceOverrideDependencies
    }

    private void readSourceOverrideDependencies() {
        sourceOverrideDependencies =
            new LinkedHashMap<SourceOverrideHandler, LinkedHashMap<String, LinkedHashMap<String,String>>>()

        NamedDomainObjectContainer<SourceOverrideHandler> sourceOverrides = project.sourceOverrides
        sourceOverrides.each { SourceOverrideHandler handler ->
            File dependencyFile = handler.dependenciesFile // TODO 2017-06-05 HughG: fix this
            def dependencyXml = new XmlSlurper(false, false).parse(dependencyFile)

            // Build hashsets from the dependency XML
            sourceOverrideDependencies[handler] =
                dependencyXml.Configuration.list().collectEntries(new LinkedHashMap<>()) { config -> [
                    config.@name.toString(),
                    config.Dependency.list().collectEntries(new LinkedHashMap<>()) { dep ->
                        GPathResult absolutePathAttrs = dep.@absolutePath
                        [
                            "${dep.@group.toString()}:${dep.@name.toString()}",
                            (
                                (absolutePathAttrs.size() == 1)
                                    ? Helper.convertPathToVersion(absolutePathAttrs[0].toString())
                                    : dep.@version.toString()
                            )
                        ]}
                ]} as LinkedHashMap<String, LinkedHashMap<String, String>>
        }
    }

    private collectConflictsForProjectVsOverride(
        List<String> conflictMessages,
        ResolvableDependencies resolvableDependencies,
        SourceOverrideHandler handler,
        Map<String, Map<String, String>> deps
    ) {
        resolvableDependencies.resolutionResult.allModuleVersions { ResolvedModuleVersionResult module ->
            deps.each { configName, config ->
                def moduleKey = "${module.id.group.toString()}:${module.id.name.toString()}"
                if (config.containsKey(moduleKey)) {
                    if (config.get(moduleKey) != module.id.version.toString()) {
                        final String message = "For ${project} configuration ${resolvableDependencies.path}, " +
                            "module '${module.id}' does not match the dependency " +
                            "declared in source override '${handler.name}' configuration ${configName} " +
                            "(${moduleKey}:${config.get(moduleKey)}); " +
                            "you may have to declare a matching source override in the source project."
                        conflictMessages << message
                    }
                }
            }
        }
    }

    private void collectConflictsBetweenOverrides(
        String projectConfiguration,
        List<String> conflictMessages,
        Map<SourceOverrideHandler, Map<String, Map<String, String>>> sourceOverrideDependencies
    ) {
        conflictMessages.addAll(getConflictsBetweenOverrides(projectConfiguration, sourceOverrideDependencies))
    }

    private List<String> getConflictsBetweenOverrides(
        String projectConfiguration,
        Map<SourceOverrideHandler, Map<String, Map<String, String>>> sourceOverrideDependencies
    ) {
        if (conflictBetweenOverrideMessages == null) {
            conflictBetweenOverrideMessages = new LinkedList<>()
            calculateConflictsBetweenOverrides(
                projectConfiguration, conflictBetweenOverrideMessages, sourceOverrideDependencies
            )
        }
        return conflictBetweenOverrideMessages
    }

    private void calculateConflictsBetweenOverrides(
        String projectConfiguration,
        List<String> conflictMessages,
        Map<SourceOverrideHandler, Map<String, Map<String, String>>> sourceOverrideDependencies
    ) {
        // The conflict check between different overrides is symmetrical, so we don't need to do it N^2 times,
        // only half that.
        Map<SourceOverrideHandler, Set<SourceOverrideHandler>> alreadyCompared =
            new HashMap<>().withDefault { new HashSet<>() }
        sourceOverrideDependencies.each {
            SourceOverrideHandler handler,
            Map<String, Map<String, String>> deps
                ->
                sourceOverrideDependencies.each {
                    SourceOverrideHandler handler2,
                    Map<String, Map<String, String>> deps2
                        ->
                        if (handler == handler2 || alreadyCompared[handler].contains(handler2)) {
                            project.logger.lifecycle "Skipping ${handler.name} <-> ${handler2.name}"
                            return
                        }
                        deps.each { configName, config ->
                            deps2.each { configName2, config2 ->
                                config.each { dep, version ->
                                    if (config2.containsKey(dep)) {
                                        project.logger.lifecycle "handler2 ${handler2.name} config ${config2} has ${dep}"
                                        final String config2DepVersion = config2.get(dep)
                                        if (config2DepVersion != version) {
                                            project.logger.lifecycle "handler2 ${handler2.name} config ${config2} " +
                                                 "has ${dep} at version ${config2DepVersion} but " +
                                                 "handler(1) ${handler.name} config ${config} has version ${version}"
                                        }
                                    } else {
                                        project.logger.lifecycle "handler2 ${handler2.name} config ${config2} " +
                                             "does NOT have ${dep}"
                                    }
                                    if (config2.containsKey(dep) && config2.get(dep) != version) {
                                        final String message = "For ${project} configuration ${projectConfiguration}, " +
                                            "module in " +
                                            "source override '${handler.name}' configuration ${configName} " +
                                            "(${dep}:${version}) " +
                                            "does not match module in " +
                                            "source override '${handler2.name}' configuration ${configName} " +
                                            "(${dep}:${config2.get(dep)}); " +
                                            "you may have to declare a matching source override."
                                        conflictMessages << message
                                    }
                                }
                            }
                        }
                        alreadyCompared[handler].add(handler2)
                        alreadyCompared[handler2].add(handler)
                }
        }
    }

    private void logOrThrowIfConflicts(
        List<String> conflictMessages,
        Configuration configuration
    ) {
        if (!conflictMessages.empty) {
            conflictMessages = conflictMessages.sort().unique()

            StringWriter stringWriter = new StringWriter()
            stringWriter.withPrintWriter { pw ->
                pw.println(
                    "The module version for one or more source overrides does not match the version " +
                        "for the same module in one or more places; please check the following messages."
                )
                for (message in conflictMessages) {
                    pw.print("    ")
                    pw.println(message)
                }
            }
            String message = stringWriter.toString()

            if (hasStrictResolutionStrategy(configuration)) {
                throw new RuntimeException(message)
            } else {
                project.logger.warn(message)
            }
        }
    }

    private static boolean hasStrictResolutionStrategy(Configuration configuration) {
        final ResolutionStrategy strategy = configuration.resolutionStrategy
        final boolean hasStrictResolutionStrategy =
            (strategy instanceof ResolutionStrategyInternal) &&
                ((ResolutionStrategyInternal) strategy).conflictResolution instanceof
                    StrictConflictResolution
        return hasStrictResolutionStrategy
    }
}

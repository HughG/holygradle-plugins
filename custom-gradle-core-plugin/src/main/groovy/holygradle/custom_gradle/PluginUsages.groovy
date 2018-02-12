package holygradle.custom_gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency

class PluginUsages {
    class Versions {
        public final String requested
        public final String selected

        Versions(String requested, String selected) {
            this.requested = requested
            this.selected = selected
        }
    }

    private Project project
    private Map<String, Versions> usages = [:]
    
    PluginUsages(Project project) {
        this.project = project

        // NOTE 2017-07-28 HughG: Strictly speaking this should find the correspondence between the requested and
        // selected versions by using a DependencyResolutionListener.  This is required because a resolutionStrategy can
        // completely change the module group and name, as well as version.  However, we don't expect anyone to do that
        // with the Holy Gradle, and this approach (assuming the group and name stay the same) is easier.
        Map<String, Versions> pluginVersions = [:]
        Configuration classpathConfiguration = project.buildscript.configurations['classpath']
        Set<Dependency> requestedHolyGradleDependencies =
                classpathConfiguration.allDependencies.findAll { it.group == "holygradle" }
        Set<ResolvedDependency> resolvedHolyGradleDependencies =
                classpathConfiguration.resolvedConfiguration.firstLevelModuleDependencies.findAll { it.group == "holygradle" }
        resolvedHolyGradleDependencies.each { ResolvedDependency dep ->
            String requestedVersion = requestedHolyGradleDependencies.find { it.name == dep.name }.version
            pluginVersions[dep.moduleName] = new Versions(requestedVersion, dep.moduleVersion)
        }
        usages = pluginVersions
    }
    
    public Map<String, Versions> getMapping() {
        usages
    }
}

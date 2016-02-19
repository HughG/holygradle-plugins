package holygradle.dependencies

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import holygradle.source_dependencies.SourceDependencyHandler
import org.apache.ivy.core.resolve.ResolvedModuleRevision
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

class SummariseAllDependenciesTask extends DefaultTask {
    public void initialize() {
        doLast {
            project.configurations.each { configuration ->
                configuration.resolve()
            }

            def file = new File(project.projectDir, "AllDependencies.xml")
            println("Writing xml to ${file.canonicalPath}")
            def markupBuilder = new StreamingMarkupBuilder()

            def xml = markupBuilder.bind {
                mkp.xmlDeclaration()
                Configurations {
                    project.configurations.each { config ->

                        Configuration(name: config.name) {
                            recursiveFlattenModules(config.resolvedConfiguration.firstLevelModuleDependencies).each { dep ->
                                // Check if this is a source dependency
                                Project sourceDep = project.dependenciesState.findModuleInBuild(
                                    new DefaultModuleVersionIdentifier(
                                        dep.moduleGroup,
                                        dep.moduleName,
                                        dep.moduleVersion
                                    )
                                )

                                Dependency(
                                    name: dep.moduleName,
                                    group: dep.moduleGroup,
                                    version: dep.moduleVersion,
                                    configuration: dep.configuration,
                                    isSource: sourceDep != null,
                                    absolutePath: sourceDep != null ? sourceDep.projectDir.toString() : ""
                                )
                            }
                        }
                    }
                }
            }

            file.text = XmlUtil.serialize(xml)
        }
    }

    public Set<ResolvedDependency> recursiveFlattenModules(Set<ResolvedDependency> input) {
        if (input.empty) {
            return []
        }

        def collected = []
        input.each { ResolvedDependency dep ->
            collected.add(recursiveFlattenModules(dep.children).flatten())
        }

        return collected.plus(input).flatten()
    }
}

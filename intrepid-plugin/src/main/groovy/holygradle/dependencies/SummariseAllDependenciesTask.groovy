package holygradle.dependencies

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

class SummariseAllDependenciesTask extends DefaultTask {
    public void initialize() {
        doLast {
            def file = new File(project.projectDir, "all-dependencies.xml")
            project.logger.info("Writing dependencies to ${file.canonicalPath}")
            def markupBuilder = new StreamingMarkupBuilder()

            Writable xml = markupBuilder.bind {
                mkp.xmlDeclaration()
                Configurations {
                    project.configurations.each((Closure){ Configuration config ->
                        Configuration(name: config.name) {
                            flattenModules(config.resolvedConfiguration.firstLevelModuleDependencies).each { dep ->
                                // Check if this is a source dependency
                                Project sourceDep = project.dependenciesState.findModuleInBuild(
                                    new DefaultModuleVersionIdentifier(
                                        dep.moduleGroup,
                                        dep.moduleName,
                                        dep.moduleVersion
                                    )
                                )

                                if (sourceDep == null) {
                                    Dependency(
                                        name: dep.moduleName,
                                        group: dep.moduleGroup,
                                        version: dep.moduleVersion,
                                        configuration: dep.configuration
                                    )
                                } else {
                                    Dependency(
                                        name: dep.moduleName,
                                        group: dep.moduleGroup,
                                        version: dep.moduleVersion,
                                        configuration: dep.configuration,
                                        sourcePath: sourceDep.projectDir.toString()
                                    )
                                }
                            }
                        }
                    })
                }
            } as Writable

            file.text = XmlUtil.serialize(xml)
        }
    }

    public Set<ResolvedDependency> flattenModules(Set<ResolvedDependency> input) {
        // Non-recursive breadth-first traversal (though we don't really care about the traversal order).
        LinkedHashSet<ResolvedDependency> collected = new LinkedHashSet<>()
        Queue<ResolvedDependency> toVisit = new LinkedList<>()
        toVisit.addAll(input)
        while (!toVisit.empty) {
            ResolvedDependency dep = toVisit.remove()
            collected.add(dep)
            toVisit.addAll(dep.children)
        }

        return collected
    }
}

package holygradle.dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor

class SummariseAllDependenciesTask extends DefaultTask {
    public void initialize() {
        GenerateIvyDescriptor generateDescriptorTask =
            project.tasks.findByName("generateIvyModuleDescriptor") as GenerateIvyDescriptor
        dependsOn generateDescriptorTask

        doLast {
            def file = new File(project.buildDir, "holygradle/flat-ivy.xml")
            project.logger.info("Writing dependencies to ${file.canonicalPath}")

            Map<ModuleVersionIdentifier, Map<String, Collection<String>>> depenenciesMap = buildDepenencies()
            XmlParser xml = new XmlParser()
            def root = xml.parse(generateDescriptorTask.destination)

            def dependenciesNode = root.dependencies as Node
            dependenciesNode.children().clear()
            depenenciesMap.each { ModuleVersionIdentifier id, Map<String, Collection<String>> confMap ->
                def allMappings = confMap.collect { String fromConf, Collection<String> toConfs ->
                    new StringBuilder() << fromConf << "->" << joinAsBuilder(toConfs, ',')
                }
                def conf = joinAsBuilder(allMappings, ";")

                TODO: deal with source dependencies

                dependenciesNode.appendNode('dependency', [
                    org: id.group,
                    name: id.name,
                    version: id.version,
                    conf: conf
                ])
            }

            file.withWriter { root.writeTo(it) }
        }
    }

    // Only public so it can be called from a lambda
    public StringBuilder joinAsBuilder(Collection<CharSequence> items, String separator) {
        StringBuilder b = new StringBuilder()
        boolean first = true
        items.each {
            if (first) {
                first = false
            } else {
                b.append(separator)
            }
            b.append(it)
        }
        return b
    }

    private Map<ModuleVersionIdentifier, Map<String, Collection<String>>> buildDepenencies() {
        Map<ModuleVersionIdentifier, Map<String, Collection<String>>> dependencies =
            [:].withDefault { [:].withDefault { [] }}

        project.configurations.each { Configuration c ->
            flattenModules(c.resolvedConfiguration.firstLevelModuleDependencies).each { ResolvedDependency d ->
                dependencies[d.module.id][c.name] << d.configuration
            }
        }

        return dependencies
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

package holygradle.dependencies

import holygradle.io.FileHelper
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
            File file = new File(project.buildDir, "holygradle/flat-ivy.xml")
            FileHelper.ensureMkdirs(file.parentFile, "for flat-ivy.xml used by source overrides")
            file.createNewFile()

            project.logger.info("Writing dependencies to ${file.canonicalPath}")

            Map<ModuleVersionIdentifier, Map<String, Collection<String>>> dependenciesMap = buildDependencies()
            XmlParser xml = new XmlParser()
            Node root = xml.parse(generateDescriptorTask.destination)

            Node dependenciesNode = root.dependencies.first() as Node
            dependenciesNode.children().clear()
            dependenciesMap.each { ModuleVersionIdentifier id, Map<String, Collection<String>> confMap ->
                List<StringBuilder> allMappings = confMap.collect { String fromConf, Collection<String> toConfs ->
                    new StringBuilder() << fromConf << "->" << joinAsBuilder(toConfs, ',')
                }
                StringBuilder conf = joinAsBuilder(allMappings, ";")

                // Make the dependency non-transitive because we want to make sure that another Holy Gradle build using
                // this file only picks up exactly the set of transitive dependencies we have calculated and flattened,
                // using the repositories in the this build.  Without that flag, the other build might resolve some
                // transitive dependencies from a different repo (in that other build script) to a newer version.
                dependenciesNode.appendNode('dependency', [
                    org: id.group,
                    name: id.name,
                    version: id.version,
                    conf: conf,
                    transitive: false
                ])
            }

            file.withWriter {
                new XmlNodePrinter(new PrintWriter(it)).print(root)
            }
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

    public Map<ModuleVersionIdentifier, Map<String, Collection<String>>> buildDependencies() {
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

package holygradle.dependencies

import groovy.util.XmlNodePrinter
import groovy.util.XmlParser
import holygradle.apache.ivy.groovy.asIvyModule
import holygradle.io.FileHelper
import holygradle.kotlin.dsl.withType
import holygradle.util.addingDefault
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import java.io.File
import java.util.*

open class SummariseAllDependenciesTask : DefaultTask() {
    private val ivyDescriptorTasks = LinkedList<GenerateIvyDescriptor>()

    fun initialize() {
        project.tasks.withType<GenerateIvyDescriptor>().whenTaskAdded { descriptorTask ->
            ivyDescriptorTasks.add(descriptorTask)
            if (ivyDescriptorTasks.size > 1) {
                project.logger.warn(
                        "WARNING: More than one Ivy file. This is not currently supported."
                )
            }
            this@SummariseAllDependenciesTask.dependsOn(descriptorTask)
        }

        doLast {
            if (ivyDescriptorTasks.size != 1) {
                throw RuntimeException(
                        "This project does not generate exactly one Ivy file. This is not currently supported."
                )
            }
            val generateDescriptorTask = ivyDescriptorTasks[0]
            val file = File(project.buildDir, "holygradle/flat-ivy.xml")
            FileHelper.ensureMkdirs(file.parentFile, "for flat-ivy.xml used by source overrides")
            file.createNewFile()

            project.logger.info("Writing dependencies to ${file.canonicalPath}")

            val dependenciesMap = buildDependencies()
            val xml = XmlParser()
            val root = xml.parse(generateDescriptorTask.destination)
            val module = root.asIvyModule()

            val dependenciesNode = module.dependencies ?: return@doLast
            dependenciesNode.clear()
            dependenciesMap.forEach { id, confMap ->
                val allMappings = confMap.map { entry ->
                    val (fromConf, toConfs) = entry
                    StringBuilder().append(fromConf).append("->").append(toConfs.joinTo(StringBuilder(), ","))
                }
                val conf = allMappings.joinTo(StringBuilder(), ";").toString()

                // Make the dependency non-transitive because we want to make sure that another Holy Gradle build using
                // this file only picks up exactly the set of transitive dependencies we have calculated and flattened,
                // using the repositories in the this build.  Without that flag, the other build might resolve some
                // transitive dependencies from a different repo (in that other build script) to a newer version.
                dependenciesNode.appendNode("dependency", mapOf(
                    "org" to id.group,
                    "name" to id.name,
                    "version" to id.version,
                    "conf" to conf,
                    "transitive" to "false"
                ))
            }

            file.printWriter().use {
                XmlNodePrinter(it).print(root)
            }
        }
    }

    private fun buildDependencies(): Map<ModuleVersionIdentifier, Map<String, Collection<String>>> {
        // TODO 2019-05-24 HughG: The Groovy code used LinkedHashMap -- is ordering important?  Don't think so, except
        // for cosmetic / debugging reasons.
        val dependencies =
            mutableMapOf<ModuleVersionIdentifier, MutableMap<String, MutableCollection<String>>>().addingDefault {
                mutableMapOf<String, MutableCollection<String>>().addingDefault { mutableListOf() }
            }

        project.configurations.forEach { c ->
            flattenModules(c.resolvedConfiguration.firstLevelModuleDependencies).forEach { d ->
                dependencies[d.module.id]!![c.name]!!.add(d.configuration)
            }
        }

        return dependencies
    }

    private fun flattenModules(input: Set<ResolvedDependency>): Set<ResolvedDependency> {
        // Non-recursive breadth-first traversal (though we don't really care about the traversal order).
        val collected = LinkedHashSet<ResolvedDependency>()
        val toVisit: Queue<ResolvedDependency> = LinkedList<ResolvedDependency>()
        toVisit.addAll(input)
        while (!toVisit.isEmpty()) {
            val dep = toVisit.remove()
            collected.add(dep)
            toVisit.addAll(dep.children)
        }

        return collected
    }
}

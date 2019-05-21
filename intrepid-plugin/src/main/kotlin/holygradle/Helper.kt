package holygradle

import holygradle.dependencies.PackedDependenciesSettingsHandler
import holygradle.kotlin.dsl.getValue
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.util.unique
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*

class Helper {
    companion object {
        val MAX_VERSION_STRING_LENGTH = 50

        // Recursively navigates down subprojects to gather the names of all sourceDependencies which
        // have not specified sourceControlRevisionForPublishedArtifacts.
        @JvmStatic
        fun getTransitiveSourceDependencies(project: Project): Collection<SourceDependencyHandler> {
            val sourceDependencies: NamedDomainObjectCollection<SourceDependencyHandler> by project.extensions
            return doGetTransitiveSourceDependencies(sourceDependencies)
        }

        // Recursively navigates down subprojects to gather the names of all sourceDependencies which
        // have not specified sourceControlRevisionForPublishedArtifacts.
        private fun doGetTransitiveSourceDependencies(
            sourceDependencies: Collection<SourceDependencyHandler>
        ): Collection<SourceDependencyHandler>  {
            // Note: Make transSourceDep be a fresh collection; previous code got ConcurrentModificationException sometimes.
            val transSourceDep = ArrayList<SourceDependencyHandler>(sourceDependencies)
            sourceDependencies.forEach { sourceDep ->
                val projName = sourceDep.targetName
                var proj = sourceDep.sourceDependencyProject
                if (proj == null) {
                    val projDir = File("${sourceDep.project.rootProject.projectDir.path}/${projName}")
                    if (projDir.exists()) {
                        proj = ProjectBuilder.builder().withProjectDir(projDir).build()
                    }
                }
                if (proj != null) {
                    // In Groovy, this was a cast to Collection<SourceDependencyHandler>; Kotlin correctly points out
                    // that that's unsafe, because you can't know the generic type of the collection elements at
                    // runtime, due to generic type information being erased.
                    val subprojSourceDep = proj.extensions.findByName("sourceDependencies") as? Collection<*>?
                    if (subprojSourceDep != null) {
                        val sourceDependencyHandlers = subprojSourceDep.filterIsInstance<SourceDependencyHandler>()
                        transSourceDep.addAll(doGetTransitiveSourceDependencies(sourceDependencyHandlers))
                    }
                }
            }
            return transSourceDep.unique()
        }

        @JvmStatic
        fun relativizePath(targetPath: File, basePath: File): String {
            val target = Paths.get(targetPath.canonicalPath)
            val base = Paths.get(basePath.canonicalPath)
            return base.relativize(target).toString()
        }

        @JvmStatic
        fun getGlobalUnpackCacheLocation(project: Project, moduleVersion: ModuleVersionIdentifier): File {
            val unpackCache = PackedDependenciesSettingsHandler.getPackedDependenciesSettings(project).unpackedDependenciesCacheDir
            val groupCache = File(unpackCache, moduleVersion.group)
            return File(groupCache, "${moduleVersion.name}-${moduleVersion.version}")
        }

        private val BUILD_TASK_REGEX = "(build|clean)(Debug|Release)/".toRegex()

        fun getProjectBuildTasks(project: Project): Map<String, Task> {
            val buildTasks: MutableCollection<Task> = mutableListOf()
            for ((_: Project, tasks: Set<Task>) in project.getAllTasks(false)) {
                buildTasks.addAll(tasks.filter { BUILD_TASK_REGEX.matches(it.name) })
            }
            return buildTasks.associate { it.name to it }
        }

        // TODO 2013-06-13 HughG: This should maybe manage a Map<String, Collection<String>> instead.
        fun parseConfigurationMapping(
            config: String,
            configurations: MutableCollection<Map.Entry<String, String>>,
            formattingErrorMessage: String
        ) {
            val split = config.split("->")
            when (split.size) {
                1 -> {
                    val configSet = config.split(",")
                    configSet.mapTo(configurations) { AbstractMap.SimpleEntry(it, it) }
                }
                2 -> {
                    val fromConfigSet = split[0].split(",")
                    val toConfigSet = split[1].split(",")
                    for (from in fromConfigSet) {
                        toConfigSet.mapTo(configurations) { AbstractMap.SimpleEntry(from, it) }
                    }
                }
                else -> throw RuntimeException(
                        formattingErrorMessage + " The configuration '$config' should be in the " +
                                "form 'aa,bb->cc,dd', where aa and bb are configurations defined in *this* build script, and cc and " +
                                "dd are configurations defined by the dependency. This will generate all pair-wise combinations of " +
                                "configuration mappings. However, if the configuration names are the same in the source and destination " +
                                "then you can simply specify comma-separated configuration names without any '->' in between e.g. 'aa,bb'. " +
                                "This will produce one configuration mapping per entry i.e. 'aa->aa' and 'bb->bb'."
                        )
            }
        }

        /**
         * Converts a file path to a version string, by hashing the path and prefixing "SOURCE".  A short, 4 character,
         * hash is used because it may itself be used as part of a file path and some parts of Windows are limited to 260
         * characters for the entire file path.
         * @param path A file path
         * @return A valid version string, of length at most {@link Helper#MAX_VERSION_STRING_LENGTH}.
         */
        fun convertPathToVersion(path: String): String {
            val canonicalPath = File(path).canonicalPath

            val sha1 = MessageDigest.getInstance("SHA1")
            val digest = sha1.digest(canonicalPath.toByteArray())
            return "SOURCE_" + digest.slice(0..3).joinToString(separator = "") { String.format("%02x", it) }
        }

    }

}


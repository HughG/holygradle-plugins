package holygradle.buildscript

import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import java.io.File

class BuildScriptDependencies(
        private val project: Project
) {
    companion object {
        internal fun initialize(project: Project): BuildScriptDependencies {
            val deps: BuildScriptDependencies = if (project == project.rootProject) {
                BuildScriptDependencies(project)
            } else {
                project.rootProject.extensions.findByName("buildScriptDependencies") as BuildScriptDependencies
            }
            project.extensions.add("buildScriptDependencies", deps)
            return deps
        }
    }

    private val dependencies = mutableMapOf<String, BuildScriptDependency>()

    @JvmOverloads
    fun add(dependencyName: String, unpack: Boolean = false, optional: Boolean = false) {
        dependencies[dependencyName] = BuildScriptDependency(project, dependencyName, unpack, optional)
    }
    
    fun getUnpackTask(dependencyName: String): Copy? = dependencies[dependencyName]?.unpackTask

    fun getPath(dependencyName: String): File? = dependencies[dependencyName]?.path
}

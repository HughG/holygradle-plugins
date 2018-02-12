package holygradle.buildscript

import holygradle.Helper
import holygradle.artifacts.ConfigurationHelper
import holygradle.custom_gradle.util.CamelCase
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.tasks.Copy
import org.gradle.script.lang.kotlin.get
import org.gradle.script.lang.kotlin.task
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import java.io.File

class BuildScriptDependency(
        project: Project,
        private val dependencyName: String,
        needsUnpacked: Boolean,
        optional: Boolean
) {
    var unpackTask: Copy? = null
        private set
    lateinit var path: File
        private set
    val unpackTaskName: String = CamelCase.build(listOf("extract", dependencyName))

    init {
        val configurationName =
            if (optional) ConfigurationHelper.OPTIONAL_CONFIGURATION_NAME else "classpath"
        val configuration = project.buildscript.configurations[configurationName]
        val firstLevelDeps =
            ConfigurationHelper.getFirstLevelModuleDependenciesForMaybeOptionalConfiguration(configuration)
        val dependencyArtifact = firstLevelDeps.firstNotNullResult { resolvedDependency ->
            resolvedDependency.allModuleArtifacts.find { it.name.startsWith(dependencyName) }
        }

        if (needsUnpacked) {
            var task: Copy? = null
            if (dependencyArtifact != null) {
                val unpackCacheLocation = Helper.getGlobalUnpackCacheLocation(project, dependencyArtifact.moduleVersion.id)
                path = unpackCacheLocation
                task = project.task<Copy>(unpackTaskName) {
                    val ext = this.extensions["ext"] as ExtraPropertiesExtension
                    ext["destinationDir"] = unpackCacheLocation
                    from(project.zipTree(dependencyArtifact.file))
                    into(unpackCacheLocation)
                }
                task.onlyIf {
                    // Checking if the target dir exists is a pretty crude way to choose whether or not to do
                    // the unpacking. Normally the 'copy' operation would do this for us, but if another instance
                    // of Gradle in another command prompt is using this dependency (e.g. extracting a package
                    // using 7zip) then the copy operation would fail. So this is really a step towards supporting
                    // intrepid concurrency but it's not really correct. a) it's still possible for two instances
                    // to execute 'copy' at the same time, and b) just because the directory exists doesn't necessarily
                    // mean it's got the right stuff in it (but running 'copy' would fix it up).
                    // Anyway, just this simple check is good enough for now.
                    !path.exists()
                }
                task.description = "Unpack build dependency '${dependencyName}'"
            }
            unpackTask = task
        } else if (dependencyArtifact != null) {
            path = dependencyArtifact.file
        }
    }
}

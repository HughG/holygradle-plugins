package holygradle.packaging

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

/**
 * This class holds all the text files to be added to a package (build script, settings file, and any other text files),
 * so that it can control the adding of a default settings file (unless overridden).
 */
class PackageArtifactTextFileCollector(private val project: Project) {
    // If this class were not factored out, it would be an easy mistake to make, to allow other code in
    // PackageArtifactDescriptor to modify textFileHandlers after it was supposed to be "fixed".

    private var buildScriptHandler: PackageArtifactBuildScriptHandler? = null
    private var settingsFileHandler: PackageArtifactSettingsFileHandler? = null
    private val textFileHandlers = mutableListOf<PackageArtifactTextFileHandler>()
    var createDefaultSettingsFile = true

    fun includeBuildScript(action: Closure<in Any?>) {
        project.logger.lifecycle("PATFC.includeBuildScriptC(${action.javaClass.name})")
        if (buildScriptHandler == null) {
            buildScriptHandler = PackageArtifactBuildScriptHandler(project).apply {
                project.logger.lifecycle("PATFC.includeBuildScriptC/apply(${this.javaClass.name})")
                checkFileHandlersNotFixedYet(this)
                ConfigureUtil.configure(action, this)
            }
        } else {
            throw RuntimeException("Can only include one build script per package.")
        }
    }

    fun includeBuildScript(action: Action<in PackageArtifactBuildScriptHandler>) {
        project.logger.lifecycle("PATFC.includeBuildScript(${action.javaClass.name})")
        if (buildScriptHandler == null) {
            buildScriptHandler = PackageArtifactBuildScriptHandler(project).apply {
                project.logger.lifecycle("PATFC.includeBuildScript/apply(${this.javaClass.name})")
                checkFileHandlersNotFixedYet(this)
                action.execute(this)
            }
        } else {
            throw RuntimeException("Can only include one build script per package.")
        }
    }

    fun includeTextFile(path: String, action: Action<PackageArtifactPlainTextFileHandler>) {
        val textFileHandler = PackageArtifactPlainTextFileHandler(path).apply {
            checkFileHandlersNotFixedYet(this)
            action.execute(this)
        }
        textFileHandlers.add(textFileHandler)
    }

    fun includeSettingsFile(action: Action<PackageArtifactSettingsFileHandler>) {
        if (settingsFileHandler == null) {
            val handler = PackageArtifactSettingsFileHandler("settings.gradle").apply {
                checkFileHandlersNotFixedYet(this)
                action.execute(this)
            }
            settingsFileHandler = handler
            textFileHandlers.add(handler)
        } else {
            throw RuntimeException("Can only include one settings file per package.")
        }
    }

    fun checkFileHandlersNotFixedYet(handler: PackageArtifactTextFileHandler) {
        if (allTextFileHandlersLazy.isInitialized()) {
            throw RuntimeException(
                "Internal error: The list of files to be packaged has been finalised, so can't add handler for " +
                handler.name
            )
        }
    }

    private val allTextFileHandlersLazy = lazy {
        val allHandlers = ArrayList<PackageArtifactTextFileHandler>(textFileHandlers.size + 2)
        val buildScriptHandler1 = buildScriptHandler
        if (buildScriptHandler1 != null && buildScriptHandler1.buildScriptRequired()) {
            allHandlers.add(buildScriptHandler1)
            // If we have a build script, by default we also want a settings file, even if it's empty.  Otherwise,
            // running a build in that folder will start looking upwards for a "settings.gradle", which almost
            // certainly is not what was intended.  The user can override that, though.
            if (settingsFileHandler == null && createDefaultSettingsFile) {
                includeSettingsFile(Action {})
            }
        }
        // Add the textFileHandlers last, in case we just added a default settings file handler.
        allHandlers.addAll(textFileHandlers)
        // Don't set allTextFileHandlers until the very end, because the includeSettingsFile call above will
        // check that it's still null.  That's so that there can't be any more files added after this method is
        // called.  If that error is ever triggered, it indicates a bug in the logic of the Intrepid plugin, most
        // likely to do with the order of the configuration-time and execution-time parts of the packaging Zip task.
        allHandlers
    }
    val allTextFileHandlers: Collection<PackageArtifactTextFileHandler> by allTextFileHandlersLazy
}

package holygradle.packaging

import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

/**
 * This class holds all the text files to be added to a package (build script, settings file, and any other text files),
 * so that it can control the adding of a default settings file (unless overridden).
 */
class PackageArtifactTextFileCollector {
    // If this class were not factored out, it would be an easy mistake to make, to allow other code in
    // PackageArtifactDescriptor to modify textFileHandlers after it was supposed to be "fixed".

    private final Project project
    private PackageArtifactBuildScriptHandler buildScriptHandler = null
    private PackageArtifactSettingsFileHandler settingsFileHandler = null
    private Collection<PackageArtifactTextFileHandler> textFileHandlers = []
    private Collection<PackageArtifactTextFileHandler> allTextFileHandlers = null
    private boolean createDefaultSettingsFile = true

    public PackageArtifactTextFileCollector(Project project) {
        this.project = project
    }

    public void includeBuildScript(Closure closure) {
        if (buildScriptHandler == null) {
            buildScriptHandler = new PackageArtifactBuildScriptHandler(project)
            checkFileHandlersNotFixedYet(buildScriptHandler)
            ConfigureUtil.configure(closure, buildScriptHandler)
        } else {
            throw new RuntimeException("Can only include one build script per package.")
        }
    }

    public void includeTextFile(String path, Closure closure) {
        PackageArtifactPlainTextFileHandler textFileHandler = new PackageArtifactPlainTextFileHandler(path)
        checkFileHandlersNotFixedYet(textFileHandler)
        ConfigureUtil.configure(closure, textFileHandler)
        textFileHandlers.add(textFileHandler)
    }

    public void includeSettingsFile(Closure closure) {
        if (settingsFileHandler == null) {
            settingsFileHandler = new PackageArtifactSettingsFileHandler("settings.gradle")
            checkFileHandlersNotFixedYet(settingsFileHandler)
            ConfigureUtil.configure(closure, settingsFileHandler)
            textFileHandlers.add(settingsFileHandler)
        } else {
            throw new RuntimeException("Can only include one settings file per package.")
        }
    }

    boolean getCreateDefaultSettingsFile() {
        return createDefaultSettingsFile
    }

    void setCreateDefaultSettingsFile(boolean create) {
        createDefaultSettingsFile = create
    }

    private void checkFileHandlersNotFixedYet(PackageArtifactTextFileHandler handler) {
        if (allTextFileHandlers != null) {
            throw new RuntimeException(
                "Internal error: The list of files to be packaged has been finalised, so can't add handler for " +
                handler.name
            )
        }
    }

    public Collection<PackageArtifactTextFileHandler> getAllTextFileHandlers() {
        if (allTextFileHandlers == null) {
            Collection<PackageArtifactTextFileHandler> allHandlers = new ArrayList(textFileHandlers.size() + 2)
            if (buildScriptHandler != null && buildScriptHandler.buildScriptRequired()) {
                allHandlers.add(buildScriptHandler)
                // If we have a build script, by default we also want a settings file, even if it's empty.  Otherwise,
                // running a build in that folder will start looking upwards for a "settings.gradle", which almost
                // certainly is not what was intended.  The user can override that, though.
                if (settingsFileHandler == null && createDefaultSettingsFile) {
                    includeSettingsFile {}
                }
            }
            // Add the textFileHandlers last, in case we just added a default settings file handler.
            allHandlers.addAll(textFileHandlers)
            // Don't set allTextFileHandlers until the very end, because the includeSettingsFile call above will
            // check that it's still null.  That's so that there can't be any more files added after this method is
            // called.  If that error is ever triggered, it indicates a bug in the logic of the Intrepid plugin, most
            // likely to do with the order of the configuration-time and execution-time parts of the packaging Zip task.
            allTextFileHandlers = allHandlers
        }
        return allTextFileHandlers
    }
}

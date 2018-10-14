package holygradle

import holygradle.io.FileHelper
import holygradle.util.unique
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.Project
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

object SettingsFileHelper {
    private object SettingsContent {
        const val SECTION_MARKER = "// holygradle source dependencies V"
        val BEGIN_PATTERN = "${SECTION_MARKER}[0-9a-f]+ BEGIN".toRegex()
        val END_PATTERN = "${SECTION_MARKER}[0-9a-f]+ END".toRegex()
        const val SETTINGS_FILE_METHOD_1_MARKER =
                "// This allows subprojects to name the gradle script the same as the directory"
        val content: String by lazy {
            val baseContent = IntrepidPlugin::class.java
                    .getResourceAsStream("/holygradle/settings-content.gradle")
                    .bufferedReader().use { it.readText() }
            val md5 = DigestUtils.md5Hex(baseContent)
            """${SECTION_MARKER}${md5} BEGIN
${baseContent}
${SECTION_MARKER}${md5} END"""
            }
    }

    // We don't just use a fixed filename because we need it to be different for tests.
    @JvmStatic
    fun getSettingsSubprojectsFile(settingsFile: File): File {
        return File(
                settingsFile.parentFile,
                "\\.[^.]+$".toRegex().replace(settingsFile.name, "-subprojects.txt")
        )
    }

    // Convert backslashes to slashes, then strip any trailing slash.
    private fun getNormalisedIncludeFilePaths(includeFilePaths: Collection<String>): Collection<String> {
        val slashes = "[\\\\]+".toRegex()
        val trailingSlash = "/$".toRegex()
        return includeFilePaths.map {
            it.replace(slashes, "/").replace(trailingSlash, "")
        }
    }

    @JvmStatic
    fun writeSettingsFile(settingsFile: File, includeFilePaths: Collection<String>): Collection<String> {
        return writeSettingsFile(settingsFile, getSettingsSubprojectsFile(settingsFile), includeFilePaths)
    }

    @JvmStatic
    fun writeSettingsFile(
        settingsFile: File,
        settingsSubprojectsFile: File,
        includeFilePaths: Collection<String>
    ): Collection<String>  {
        val normalisedIncludeFilePaths = getNormalisedIncludeFilePaths(includeFilePaths)
        settingsSubprojectsFile.printWriter().use { w ->
            for (path in normalisedIncludeFilePaths) {
                w.println(path)
            }
        }

        // The settings file is always going to be relatively small, so it's okay to handle all the lines in memory.
        if (settingsFile.exists()) {
            val lines = settingsFile.readLines()
            if (lines.any { it.contains(SettingsContent.SETTINGS_FILE_METHOD_1_MARKER) }) {
                // The settings file was auto-generated by older versions of the Holy Gradle, so delete it and replace
                // It with the current version.  (There was no specific marker for that code, so we just match some
                // text which it happens to have always included.)
                FileHelper.ensureDeleteFile(settingsFile)
                appendSettingsFileContent(settingsFile)
            } else {
                // The settings file exists and doesn't contain the older style of holygradle code.  So, it might
                // or might not contain the new style.  If it does, then we copy all lines before and after the
                // BEGIN/END markers, and add the new lines (with markers) at the same position as the original.
                // If it doesn't, we'll append the current version of the code.
                if (!replaceSettingsFileContent(settingsFile, lines)) {
                    // The settings file didn't contain any version of the holygradle code, so add current version.
                    appendSettingsFileContent(settingsFile)
                }
            }
        } else {
            // The settings file doesn't exist, so create it with the current version of the holygradle code.
            appendSettingsFileContent(settingsFile)
        }

        return normalisedIncludeFilePaths
    }

    private fun appendSettingsFileContent(settingsFile: File) {
        FileOutputStream(settingsFile, true).bufferedWriter().use { bw ->
            PrintWriter(bw).use { w ->
                /*
                 * This intentionally starts with a blank line, in case the file didn't end with a line ending.
                 * In that case, this text would be appended at the end of that line, so the start would not be
                 * recognised on later runs, and so it would keep being appended on later runs.
                 */
                w.println()
                // We pull the lines out of the string and write them individually to be sure we get platform-specific
                // line endings right.
                for (line in SettingsContent.content.lines()) {
                    w.println(line)
                }
            }
        }
    }

    private fun replaceSettingsFileContent(settingsFile: File, lines: List<String>): Boolean {
        val newLines = ArrayList<String>(lines.size * 2) // extra capacity in case new version is larger
        var replaced = false
        var replacing = false
        for (line in lines) {
            if (!replacing) {
                if (line.matches(SettingsContent.BEGIN_PATTERN)) {
                    // Start replacing, including this line.
                    replacing = true
                } else {
                    // Copy this line to the new settings file.
                    newLines.add(line)
                }
            } else { // we are replacing this section
                if (line.matches(SettingsContent.END_PATTERN)) {
                    // Insert the new content, and stop skipping.
                    newLines.addAll(SettingsContent.content.lines())
                    replacing = false
                    replaced = true
                } else {
                    // Don't copy this line to the new settings file, because we're replacing it.
                }
            }
        }
        // If we actually replaced some lines, re-write the settings file (from scratch -- not "append").
        if (replaced) {
            settingsFile.printWriter().use { w ->
                for (line in newLines) {
                    w.println(line)
                }
            }
        }
        return replaced
    }

    // Returns true if the settings have changed.
    // Only public for testing.
    @JvmStatic
    fun writeSettingsFileAndDetectChange(settingsFile: File, includeFilePaths: Collection<String>): Boolean {
        val previousIncludeFilePaths = mutableListOf<String>()

        val settingsSubprojectsFile = getSettingsSubprojectsFile(settingsFile)
        if (settingsSubprojectsFile.exists()) {
            previousIncludeFilePaths.addAll(settingsSubprojectsFile.readLines())
        }

        // We write the settings file even if we have an empty list of includeFilePaths, because the user might have
        // removed all sourceDependencies, as compared to the previous run.
        val newIncludeFilePaths = writeSettingsFile(settingsFile, settingsSubprojectsFile, includeFilePaths).sorted()

        previousIncludeFilePaths.sort()

        return newIncludeFilePaths != previousIncludeFilePaths
    }
    
    // Returns true if the settings have changed.
    @JvmStatic
    fun writeSettingsFileAndDetectChange(project: Project): Boolean {
        val settingsFile = File(project.projectDir, "settings.gradle")
        val transitiveSubprojects = Helper.getTransitiveSourceDependencies(project)
        val newIncludes = transitiveSubprojects.map {
            Helper.relativizePath(it.destinationDir, project.rootProject.projectDir)
        }
        return writeSettingsFileAndDetectChange(settingsFile, newIncludes.unique())
    }
}
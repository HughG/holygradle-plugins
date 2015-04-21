package holygradle

import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project

import java.util.regex.Matcher

class SettingsFileHelper {
    private static final String SETTINGS_SUBPROJECTS_FILE_NAME = 'settings-subprojects.txt'
    private static final String HOLY_GRADLE_SOURCE_DEPENDENCIES_SECTION_MARKER = '// holygradle source dependencies V2'
    private static final String SETTINGS_FILE_METHOD_1_MARKER =
        '// This allows subprojects to name the gradle script the same as the directory'
    private static final String SETTINGS_FILE_CONTENT = """${HOLY_GRADLE_SOURCE_DEPENDENCIES_SECTION_MARKER} BEGIN
// Do not edit this section.  It may be replaced by the holygradle plugins automatically.
final List<String> subprojectPaths = new File(settingsDir, '${SETTINGS_SUBPROJECTS_FILE_NAME}').readLines().findAll {
    !it.startsWith('#') && !it.trim().isEmpty()
}
subprojectPaths.each { include it }
// This method does two things for sub-projects in a multi-project build.
//   1) Allows them to lie outside the root project's folder (by removing relative paths from the start of the name).
//   2) Allows them to have their build files named to match the directory.
void adjustProjects(ProjectDescriptor proj) {
    String projBaseName = (proj.name =~ /.*?([\\w_\\-]+)\$/)[0][1]
    proj.name = projBaseName
    Collection<File> buildFiles = [proj.name, 'build'].collect { new File(proj.projectDir, "\${it}.gradle") }.findAll { it.exists() }
    switch (buildFiles.size()) {
        case 0:
            println("No Gradle build file found for subproject \${proj.name} in \${proj.projectDir}; excluding from build.")
            project.parent.children.remove(proj)
            break;
        case 1:
            proj.buildFileName = buildFiles[0].name
            break;
        case 2:
            throw new RuntimeException("For \${proj.name} you have '\${proj.name}.gradle' AND 'build.gradle'. You should only have one of these.")
        default:
            throw new RuntimeException("Internal error: more than 2 build files! \${buildFiles}.")
    }
    proj.children.each { adjustProjects(it) }
}
adjustProjects(rootProject)
${HOLY_GRADLE_SOURCE_DEPENDENCIES_SECTION_MARKER} END"""

    // We don't just use a fixed filename because we need it to be different for tests.
    private static File getSettingsSubprojectsFile(File settingsFile) {
        return new File(
            settingsFile.parentFile,
            settingsFile.name.replaceAll(/\.[^\.]+$/, "-subprojects.txt")
        )
    }

    // Convert backslashes to slashes, then strip any trailing slash.
    private static Collection<String> getNormalisedIncludeFilePaths(Collection<String> includeFilePaths) {
        return includeFilePaths.collect {
            it.replaceAll(/[\\\/]+/, '/').replaceAll(/\/$/, '')
        }
    }

    public static Collection<String> writeSettingsFile(File settingsFile, Collection<String> includeFilePaths) {
        return writeSettingsFile(settingsFile, getSettingsSubprojectsFile(settingsFile), includeFilePaths)
    }

    public static Collection<String> writeSettingsFile(
        File settingsFile,
        File settingsSubprojectsFile,
        Collection<String> includeFilePaths
    ) {
        includeFilePaths = getNormalisedIncludeFilePaths(includeFilePaths)
        settingsSubprojectsFile.withPrintWriter { w ->
            includeFilePaths.each { w.println(it) }
        }

        // Delete any settings file which was auto-generated by previous versions of the Holy Gradle.
        // There was no specific marker for it, so we just match some text which it happens to have always included.
        if (settingsFile.exists() &&
            settingsFile.readLines().find { it.contains(SETTINGS_FILE_METHOD_1_MARKER) }
        ) {
            if (!settingsFile.delete()) {
                throw new RuntimeException("Failed to delete existing settings.gradle file.")
            }
        }

        // Append our magic subproject setup code if it's not already there.
        if (!settingsFile.exists() ||
            !settingsFile.readLines().find { it.startsWith(HOLY_GRADLE_SOURCE_DEPENDENCIES_SECTION_MARKER) }
        ) {
            settingsFile.withWriterAppend { BufferedWriter bw ->
                bw.withPrintWriter { PrintWriter w ->
                    // We pull the lines out of the string and write them individually to be sure we get platform-specific
                    // line endings right.
                    SETTINGS_FILE_CONTENT.readLines().each { w.println(it) }
                }
            }
        }

        return includeFilePaths
    }

    // Returns true if the settings have changed.
    public static boolean writeSettingsFileAndDetectChange(File settingsFile, Collection<String> includeFilePaths) {
        List<String> previousIncludeFilePaths = []

        File settingsSubprojectsFile = getSettingsSubprojectsFile(settingsFile)
        if (settingsSubprojectsFile.exists()) {
            previousIncludeFilePaths = settingsSubprojectsFile.readLines()
        }

        // We write the settings file even if we have an empty list of includeFilePaths, because the user might have
        // removed all sourceDependencies, as compared to the previous run.
        List<String> newIncludeFilePaths = writeSettingsFile(settingsFile, settingsSubprojectsFile, includeFilePaths)

        Collections.sort(previousIncludeFilePaths)
        Collections.sort(newIncludeFilePaths)

        return newIncludeFilePaths != previousIncludeFilePaths
    }
    
    // Returns true if the settings have changed.
    public static boolean writeSettingsFileAndDetectChange(Project project) {
        File settingsFile = new File(project.projectDir, "settings.gradle")
        Collection<SourceDependencyHandler> transitiveSubprojects = Helper.getTransitiveSourceDependencies(project)
        Collection<String> newIncludes = transitiveSubprojects.collect {
            Helper.relativizePath(it.getDestinationDir(), project.rootProject.projectDir)
        }
        writeSettingsFileAndDetectChange(settingsFile, newIncludes.unique())
    }
}
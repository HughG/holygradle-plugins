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
final List<String> subprojectPaths = new File(settingsDir, '${SETTINGS_SUBPROJECTS_FILE_NAME}').readLines().findAll { !it.startsWith('#') }
subprojectPaths.each { include it }
// This method means sub-projects in a multi-project build can have their build files named to match the directory.
void adjustChildrenBuildFiles(ProjectDescriptor proj) {
    Collection<File> buildFiles = [proj.name, 'build'].collect { new File(proj.projectDir, "\${it}.gradle") }.findAll { it.exists() }
    switch (buildFiles.size()) {
        case 0:
            println("No Gradle build file found for subproject \${projName} in \${it.projectDir}; excluding from build.")
            project.parent.children.remove(proj)
            break;
        case 1:
            proj.buildFileName = buildFiles[0].name
            break;
        case 2:
            throw new RuntimeException("For \${projName} you have '\${projName}.gradle' AND 'build.gradle'. You should only have one of these.")
        default:
            throw new RuntimeException("Internal error: more than 2 build files! \${buildFiles}.")
    }
    proj.children.each { adjustChildrenBuildFiles(it) }
}
adjustChildrenBuildFiles(rootProject)
${HOLY_GRADLE_SOURCE_DEPENDENCIES_SECTION_MARKER} END"""

    // Convert slashes to colons, then strip trailing colon.
    private static Collection<String> getIncludeProjectPaths(Collection<String> includeFilePaths) {
        return includeFilePaths.collect {
            it.replaceAll(/[\\\/]+/, ':').replaceAll(/:$/, '')
        }
    }

    public static Collection<String> writeSettingsFile(File settingsFile, Collection<String> includeFilePaths) {
        Collection<String> includeProjectPaths = getIncludeProjectPaths(includeFilePaths)
        File settingsSubprojectsFile = new File(settingsFile.parentFile, SETTINGS_SUBPROJECTS_FILE_NAME)
        settingsSubprojectsFile.withPrintWriter { w ->
            includeProjectPaths.each { w.println(it) }
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

        return includeProjectPaths
    }
    
    // Returns true if the settings have changed.
    public static boolean writeSettingsFileAndDetectChange(File settingsFile, Collection<String> includeFilePaths) {
        List<String> previousIncludeProjectPaths = []

        File settingsSubprojectsFile = new File(settingsFile.parentFile, SETTINGS_SUBPROJECTS_FILE_NAME)
        if (settingsSubprojectsFile.exists()) {
            previousIncludeProjectPaths = settingsSubprojectsFile.readLines()
        }

        // We write the settings file even if we have an empty list of includeFilePaths, because the user might have
        // removed all sourceDependencies, as compared to the previous run.
        List<String> newIncludeProjectPaths = writeSettingsFile(settingsFile, includeFilePaths)

        Collections.sort(previousIncludeProjectPaths)
        Collections.sort(newIncludeProjectPaths)

        return newIncludeProjectPaths != previousIncludeProjectPaths
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
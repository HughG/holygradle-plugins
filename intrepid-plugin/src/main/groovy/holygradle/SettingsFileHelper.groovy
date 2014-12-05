package holygradle

import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project

import java.util.regex.Matcher

class SettingsFileHelper {
        
    public static String writeSettingsFile(File settingsFile, Collection<String> includePaths) {
        Collection<String> escapedPaths = includePaths.collect {
            String p = it.replaceAll(/[\\\/]*$/, '').replace("\\", "\\\\")
            "'${p}'"
        }
        String newIncludes = "include " + escapedPaths.join(", ")
        final String settingsFileBody = """rootProject.children.removeAll { ProjectDescriptor it ->
    String projName = (it.name =~ /.*?([\\w_\\-]+)\$/)[0][1]
    it.name = projName
    // This allows subprojects to name the gradle script the same as the directory e.g. ..\\foo\\foo.gradle
    boolean projectNamedScript = new File(it.projectDir, projName + '.gradle').exists()
    boolean defaultNamedScript = new File(it.projectDir, "build.gradle").exists()
    if (projectNamedScript && defaultNamedScript) {
        throw new RuntimeException("For \${projName} you have '\${projName}.gradle' AND 'build.gradle'. You should only have one of these.")
    } else if (projectNamedScript) {
        it.buildFileName = projName + '.gradle'        
        return false // this folder has a valid project script so keep it
    } else if (defaultNamedScript) {
        return false // this folder has a valid project script so keep it
    }
    println("Warning: no gradle file found for subproject \${projName} in \${it.projectDir}, excluding from build.")
    return true
}"""

        settingsFile.withPrintWriter { w ->
            if (!escapedPaths.empty) {
                w.println(newIncludes)
            }
            // We pull the lines out of the string and write them individually to be sure we get platform-specific
            // line endings right.
            settingsFileBody.readLines().each { w.println(it) }
        }

        return newIncludes
    }
    
    // Returns true if the settings have changed.
    public static boolean writeSettingsFileAndDetectChange(File settingsFile, Collection<String> includePaths) {
        List<String> previousIncludes = []
        if (settingsFile.exists()) {
            String includeText = settingsFile.text.readLines()[0]
            Matcher matches = includeText =~ /['"](.+?)['"]/
            matches.each { List<String> groups ->
                previousIncludes.add groups[1].replace("\\\\", "\\")
            }
        }

        if (includePaths.size() > 0) {
            writeSettingsFile(settingsFile, includePaths)
        }
        
        List<String> addedIncludes = (List<String>)includePaths.clone()
        addedIncludes.removeAll(previousIncludes)
        
        return addedIncludes.size() > 0
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
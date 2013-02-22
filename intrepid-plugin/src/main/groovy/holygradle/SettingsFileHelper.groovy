package holygradle

import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.FileWriter
import java.nio.file.Paths

class SettingsFileHelper {      
        
    public static String writeSettingsFile(File settingsFile, def includePaths) {
        def newIncludes = "include " + includePaths.collect { "'${it}'" }.join(", ")
        def settings = newIncludes
        
        // regex below is: /.*?([\w_\-]+)$/
        settings += "\r\nrootProject.children.each {"
        settings += "\r\n    def projName = (it.name =~ /.*?([\\w_\\-]+)\$/)[0][1]"
        settings += "\r\n    // This allows subprojects to be named the same as the directory."
        settings += "\r\n    it.name = projName"            
        settings += "\r\n    // This allows subprojects to name the gradle script the same as the directory e.g. ..\\foo\\foo.gradle"
        settings += "\r\n    boolean projectNamedScript = new File(it.projectDir, projName + '.gradle').exists()"
        settings += "\r\n    boolean defaultNamedScript = new File(it.projectDir, \"build.gradle\").exists()"
        settings += "\r\n    if (projectNamedScript && defaultNamedScript) {"
        settings += "\r\n        throw new RuntimeException(\"For \${projName} you have '\${projName}.gradle' AND 'build.gradle'. You should only have one of these.\")"
        settings += "\r\n    } else if (projectNamedScript) {"
        settings += "\r\n        it.buildFileName = projName + '.gradle'"
        settings += "\r\n    }"
        settings += "\r\n}"
        
        def writer = new FileWriter(settingsFile)
        writer.write(settings)
        writer.close()
        
        return newIncludes
    }
    
    // Returns true if the settings have changed.
    public static boolean writeSettingsFile(Project project) {
        def settingsFile = new File(project.projectDir, "settings.gradle")
        def previousIncludes = []
        if (settingsFile.exists()) {
            def includeText = settingsFile.text.readLines()[0]
            def matches = includeText =~ /[\'\"](.+?)[\'\"]/
            matches.each {
                previousIncludes.add it[1]
            }
        }
        def transitiveSubprojects = Helper.getTransitiveSourceDependencies(project)
        def newIncludes = transitiveSubprojects.collect {
            def relPath = Helper.relativizePath(it.getDestinationDir(), project.rootProject.projectDir)
            relPath.replaceAll(/[\\\/]*$/, '').replace("\\", "\\\\")
        }
        newIncludes = newIncludes.unique()
        if (newIncludes.size() > 0) {
            writeSettingsFile(settingsFile, newIncludes)
        }
        
        def addedIncludes = newIncludes
        addedIncludes.removeAll(previousIncludes)
        
        return addedIncludes.size() > 0
    }
}
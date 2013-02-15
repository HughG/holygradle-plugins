package holygradle

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.testfixtures.*
import java.io.FileWriter
import java.nio.file.Paths

class SettingsFileHelper {      
    // Recursively navigates down subprojects to gather the names of all sourceDependencies which
    // have not specified sourceControlRevisionForPublishedArtifacts.
    private static def getTransitiveSourceDependencies(Project project, def sourceDependencies) {
        def transSourceDep = sourceDependencies
        sourceDependencies.each { sourceDep ->
            def projName = sourceDep.getTargetName()
            def proj = project.findProject(projName)
            if (proj == null) {
                def projDir = new File("${project.rootProject.projectDir.path}/${projName}")
                if (projDir.exists()) {
                    proj = ProjectBuilder.builder().withProjectDir(projDir).build()
                }
            }
            if (proj != null) {
                def subprojSourceDep = proj.extensions.findByName("sourceDependencies")
                if (subprojSourceDep != null) {
                    def transSubprojSourceDep = getTransitiveSourceDependencies(project, subprojSourceDep)
                    transSourceDep = transSourceDep + transSubprojSourceDep
                }
            }
        }
        return transSourceDep.unique()
    }
    
    // Returns true if the settings have changed.
    public static boolean writeSettingsFile(Project project, boolean subprojectScriptsNamedAsFolder) {
        def settingsFile = new File(project.projectDir, "settings.gradle")
        def previousIncludes = ""
        if (settingsFile.exists()) {
            previousIncludes = settingsFile.text.readLines()[0]
        }
        def sourceDependencies = project.sourceDependencies
        def transitiveSubprojects = getTransitiveSourceDependencies(project, sourceDependencies)
        def subprojectPaths = transitiveSubprojects.collect {
            def relativePath = Helper.relativizePath(it.getDestinationDir(), project.rootProject.projectDir)
            def escapedPath = relativePath.replaceAll(/[\\\/]*$/, '').replace("\\", "\\\\")
            "'${escapedPath}'"
        }
        subprojectPaths = subprojectPaths.unique()
        def newIncludes = ""
        if (subprojectPaths.size() > 0) {
            newIncludes = "include " + subprojectPaths.join(", ")
            def settings = newIncludes
            
            // regex below is: /.*?([\w_\-]+)$/
            settings += "\r\nrootProject.children.each {"
            settings += "\r\n    def projName = (it.name =~ /.*?([\\w_\\-]+)\$/)[0][1]"
            settings += "\r\n    // This allows subprojects to be named the same as the directory."
            settings += "\r\n    it.name = projName"
            if (subprojectScriptsNamedAsFolder) {
                settings += "\r\n    // This allows subprojects to name the gradle script the same as the directory e.g. ..\\foo\\foo.gradle"
                settings += "\r\n    it.buildFileName = projName + '.gradle'"
            }
            settings += "\r\n}"
            def writer = new FileWriter(settingsFile)
            writer.write(settings)
            writer.close()
        }
        return previousIncludes != newIncludes
    }
}
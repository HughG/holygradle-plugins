package holygradle

import org.gradle.*
import org.gradle.api.*
import org.gradle.testfixtures.*
import org.gradle.logging.*

class RecursivelyFetchSourceTask extends DefaultTask {
    boolean generateSettingsFileForSubprojects = true
    boolean subprojectScriptsNamedAsFolder = true
    boolean recursive = true
    
    RecursivelyFetchSourceTask() {
        /*println "-------> Configuring '${project.name}' tasks."
        println "  rootProject: " + project.rootProject.name
        println "  project: " + project.name
        println "  parent: " + project.parent
        doFirst {
            println "Started fetching '${project.name}'. Start param: " + project.gradle.startParameter.newInstance()
        }*/
        doLast {
            def sourceDepTasks = []
            taskDependencies.getDependencies(this).each { t ->
                if (t instanceof FetchSourceDependencyTask && t.getDidWork()) {
                    sourceDepTasks.add(t)
                }
            }
            boolean needToReRun = false
            if (generateSettingsFileForSubprojects) {
                needToReRun = SettingsFileHelper.writeSettingsFile(project.rootProject, subprojectScriptsNamedAsFolder)
                if (sourceDepTasks.size() > 0) {
                    needToReRun = true
                }
            }
            if (recursive) {
                def command = System.getProperty("sun.java.command").split(" ")[0]
                //println "command: $command"
                if (command.contains("daemon")) {
                    // The daemon is being used so we can't terminate the process directly. 
                    // Attempt to recurse down to subproject dependencies, but this won't work very well.
                    sourceDepTasks.each { t ->
                        def sourceDirName = t.getSourceDirName()
                        def startParam = project.gradle.startParameter.newInstance()
                        startParam.setBuildFile(new File(t.destinationDir, sourceDirName + ".gradle"))
                        startParam.setCurrentDir(project.rootProject.projectDir)
                        startParam.setTaskNames(["fetchAllDependencies"])
                        def launcher = GradleLauncher.newInstance(startParam)
                        launcher.run()
                    }
                    if (needToReRun) {
                        println "-"*80
                        println "Additional subprojects may exist now, and their dependencies might not be satisfied."
                        println "Please re-run this task to fetch transitive dependencies."
                        println "-"*80
                    }
                } else {
                    // We're not using the daemon, so we can exit the process and return a special exit code 
                    // which will cause the process to be rerun by the gradle wrapper.
                    if (needToReRun) {
                        println "-"*80
                        println "Additional subprojects may exist now. Rerunning this task..."
                        println "-"*80
                        System.exit(123456)
                    }
                }
            }
        }
    }
}
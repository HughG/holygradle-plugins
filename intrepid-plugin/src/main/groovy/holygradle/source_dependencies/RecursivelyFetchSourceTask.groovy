package holygradle.source_dependencies

import org.gradle.*
import org.gradle.api.*
import holygradle.SettingsFileHelper

class RecursivelyFetchSourceTask extends DefaultTask {
    public boolean generateSettingsFileForSubprojects = true
    public boolean recursive = true
    private boolean addedUnpackTasks

    RecursivelyFetchSourceTask() {
        /*println "-------> Configuring '${project.name}' tasks."
        println "  rootProject: " + project.rootProject.name
        println "  project: " + project.name
        println "  parent: " + project.parent
        doFirst {
            println "Started fetching '${project.name}'. Start param: " + project.gradle.startParameter.newInstance()
        }*/
        doLast {
            if (!getAddedUnpackTasks()) {
                throw new RuntimeException(
                    "The '${this.name}' task can only be specified explicitly on the command line, " +
                    "not run implicitly as a dependency of other tasks."
                )
            }

            Collection<FetchSourceDependencyTask> sourceDepTasks = []
            taskDependencies.getDependencies(this).each { t ->
                if (t instanceof FetchSourceDependencyTask && t.getDidWork()) {
                    sourceDepTasks.add(t)
                }
            }
            boolean needToReRun = false
            if (generateSettingsFileForSubprojects) {
                needToReRun = SettingsFileHelper.writeSettingsFileAndDetectChange(project.rootProject)
                if (sourceDepTasks.size() > 0) {
                    needToReRun = true
                }
            }
            if (recursive) {
                String command = System.getProperty("sun.java.command").split(" ")[0]
                //println "command: $command"
                if (command.contains("daemon")) {
                    // The daemon is being used so we can't terminate the process directly. 
                    // Attempt to recurse down to subproject dependencies, but this won't work very well.
                    sourceDepTasks.each { t ->
                        String sourceDirName = t.getSourceDirName()
                        StartParameter startParam = project.gradle.startParameter.newInstance()
                        startParam.setBuildFile(new File(t.destinationDir, sourceDirName + ".gradle"))
                        startParam.setCurrentDir(project.rootProject.projectDir)
                        startParam.setTaskNames(["fetchAllDependencies"])
                        GradleLauncher launcher = GradleLauncher.newInstance(startParam)
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

    public void setAddedUnpackTasks() {
        addedUnpackTasks = true
    }

    public boolean getAddedUnpackTasks() {
        return addedUnpackTasks
    }

}
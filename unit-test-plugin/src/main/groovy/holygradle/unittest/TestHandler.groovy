package holygradle.unittest

import org.gradle.api.*


class TestHandler {
    public final String name
    private def commandLineChunks = []
    private String redirectOutputFilePath = null
    private def selectedFlavours = ["Debug", "Release"]
    
    public static def createContainer(Project project) {
        project.extensions.tests = project.container(TestHandler)
        project.extensions.tests
    }
    
    public TestHandler(String name) {
        this.name = name
    }
    
    public void commandLine(String... cmdLine) {
        for (c in cmdLine) {
            commandLineChunks.add(c)
        }
    }
    
    public void redirectOutputToFile(String outputFilePath) {
        redirectOutputFilePath = outputFilePath
    }
    
    public void flavour(String... newflavours) {
        selectedFlavours.clear()
        for (f in newflavours) {
            selectedFlavours.add(f)
        }
    }
    
    private void configureTask(Project project, String flavour, Task task) {
        if (selectedFlavours.contains(flavour)) {
            def testMessage = "Running unit test ${name} (${flavour})..."
            task.doLast {
                println testMessage
                if (commandLineChunks.size() == 0) {
                    println "    Nothing to run."
                } else {
                    project.exec {
                        def cmd = commandLineChunks.collect { replaceFlavour(it, flavour) }
                        def exePath = new File(cmd[0])
                        if (!exePath.exists()) {
                            def tryPath = new File(project.projectDir, cmd[0])
                            if (tryPath.exists()) {
                                exePath = tryPath
                            }
                        }
                        if (!exePath.exists()) {
                            def tryPath = new File(project.rootProject.projectDir, cmd[0])
                            if (tryPath.exists()) {
                                exePath = tryPath
                            }
                        }
                        cmd[0] = exePath.path
                        commandLine cmd
                        if (redirectOutputFilePath != null) {
                            standardOutput = new FileOutputStream("${project.projectDir}/${replaceFlavour(redirectOutputFilePath, flavour)}") 
                        }
                    }
                }
            }
        }
    }
    
    public static void preDefineTasks(Project project) {
        TestFlavourHandler.getAllFlavours(project).each { flavour ->
            project.task("unitTest${flavour}", type: DefaultTask)
        }
    }
    
    public static void defineTasks(Project project) {
        def allFlavours = TestFlavourHandler.getAllFlavours(project)
        for (flavour in allFlavours) {
            def buildDependencies = project.extensions.findByName("buildDependencies")
            boolean anyBuildDependencies = buildDependencies.size() > 0
            
            def unitTestThisProject = null
            if (anyBuildDependencies) {
                unitTestThisProject = project.task("unitTest${flavour}Independently", type: DefaultTask)
            } else {
                unitTestThisProject = project.task("unitTest${flavour}", type: DefaultTask)
            }
            unitTestThisProject.group = "Unit Test"
            unitTestThisProject.description = "Run the ${flavour} unit tests for '${project.name}'."
                
            project.extensions.tests.each {
                it.configureTask(project, flavour, unitTestThisProject)
            }
            
            if (anyBuildDependencies) {
                def task = project.task("unitTest${flavour}", type: DefaultTask)
                task.group = "Unit Test"
                task.description = "Run the ${flavour} unit tests for '${project.name}' and all dependent projects."
                task.dependsOn unitTestThisProject
                
                def taskDependencies = project.extensions.findByName("taskDependencies")
                taskDependencies.configureNow("unitTest${flavour}")
            }
        }
        if (allFlavours.size() > 1) {
            def buildReleaseTask = project.tasks.findByName("buildRelease")
            def buildDebugTask = project.tasks.findByName("buildDebug")
            if (buildReleaseTask != null && buildDebugTask != null) {
                def buildAndTestAll = project.task("buildAndTestAll", type: DefaultTask) {
                    description = "Build '${project.name}' and all dependent projects in Debug and Release, and run unit tests."
                }
                for (flavour in allFlavours) {
                    buildAndTestAll.dependsOn "unitTest${flavour}"
                }
                buildAndTestAll.dependsOn buildDebugTask, buildReleaseTask
            }
        }
    }
    
    private static String replaceFlavour(String input, String flavour) {
        def lower = flavour.toLowerCase()
        def upper = flavour.toUpperCase()
        input
            .replace("<flavour>", lower)
            .replace("<Flavour>", upper[0] + lower[1..-1])
            .replace("<FLAVOUR>", upper)
            .replace("<f>", lower[0])
            .replace("<F>", upper[0])
    }
}
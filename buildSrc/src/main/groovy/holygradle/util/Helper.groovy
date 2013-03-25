package holygradle.util

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

class Helper {
    public static String getLatestVersionNumber(Project project, String group, String moduleName) {
        def latestVersion = null
        def externalDependency = new DefaultExternalModuleDependency(group, moduleName, "+", "default")
        def dependencyConf = project.configurations.detachedConfiguration(externalDependency)
        dependencyConf.resolutionStrategy.cacheDynamicVersionsFor 1, 'seconds'
        
        try {
            dependencyConf.getResolvedConfiguration().getFirstLevelModuleDependencies().each {
                latestVersion = it.getModuleVersion()
            }
        } catch (Exception e) {
        }
        
        latestVersion
    }
    
    public static void choosePluginVersion(Project project, String confName, String group, String moduleName) {
        if (project.gradle.taskGraph.hasTask(project.publishRelease)) {
            def latestVersion = getLatestVersionNumber(project, group, moduleName)
            if (latestVersion == null) {
                throw new RuntimeException("${group}:${moduleName} is stated as a dependency, but it has not previously been published. Please publish that module first.")  
            }
            project.dependencies.add(confName, "${group}:${moduleName}:${latestVersion}")
        } else {
            def username = System.getProperty("user.name").toLowerCase()
            project.dependencies.add(confName, "${group}:${moduleName}:${username}-SNAPSHOT") { changing = true }
        }
    }
    
    public static void chooseNextVersionNumberWithUserInput(Project project) {
        def latestVersion = getLatestVersionNumber(project, project.group, project.name)
        
        Console console = System.console()
        if (console == null) {
            // Work-around for this bug: http://issues.gradle.org/browse/GRADLE-2310
            println "Null System.console(). Are you using the Gradle Daemon? If so, don't. "
            println "It prevents acquiring user input. Please use: --no-daemon"
            throw new RuntimeException("Beware the daemon.")
        }
        
        def proposedVersion = null
        if (latestVersion == null) {
            proposedVersion = "1.0.0.0"
            println "Failed to determine the most recent version of ${project.group}:${project.name}."
            println "Maybe you can't connect to your repository, or maybe you've never published this"
            println "module before. Proposing '${proposedVersion}' as the version number."
        } else {
            console.printf "\n\nLatest version   : ${latestVersion}"
            def versionStr = latestVersion
            int lastDot = versionStr.lastIndexOf('.')
            int nextVersion = versionStr[lastDot+1..-1].toInteger() + 1
            def firstChunk = versionStr[0..lastDot]
            proposedVersion = firstChunk + nextVersion.toString()
        }
        
        console.printf "\nProposed version : ${proposedVersion}"
        
        def userVersionNumber = console.readLine("\nIs this ok? (Press enter to accept, or type in a new version number.) : ")
        if (userVersionNumber == null || userVersionNumber == "") {
            project.version = proposedVersion
        } else {
            project.version = userVersionNumber
        }
        
        console.printf "\nPublishing '${project.group}:${project.name}:${project.version}'...\n\n"
    }
}
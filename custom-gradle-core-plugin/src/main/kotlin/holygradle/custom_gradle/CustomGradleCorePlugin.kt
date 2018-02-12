package holygradle.custom_gradle

import holygradle.custom_gradle.util.ProfilingHelper
import holygradle.custom_gradle.util.VersionNumber
import holygradle.io.FileHelper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.wrapper.Wrapper
import java.io.File

import java.nio.file.Files
import org.gradle.script.lang.kotlin.task

class CustomGradleCorePlugin : Plugin<Project> {
    /**
     * Returns the path to the <tt>custom-gradle</tt> init script.
     * @param project The project to which this plugin is applied.
     * @return The path to the <tt>custom-gradle</tt> init script.
     */
    private fun getInitScriptLocation(project: Project): String? =
        project.gradle.startParameter.allInitScripts.find { it.name == "holy-gradle-init.gradle" }?.name

    override fun apply(target: Project?) {
        val project = target!!
        val profilingHelper = ProfilingHelper(project.logger)
        val timer = profilingHelper.startBlock("CustomGradleCorePlugin#apply(${target})")

        val gradlePropsFile = File(project.gradle.gradleUserHomeDir, "gradle.properties")

        // DSL extension 'prerequisites' to allow build script to declare and verify prerequisites.
        val prerequisites = PrerequisitesExtension.defineExtension(target)
               
        prerequisites.specify("Java", "1.7").check()
               
        // DSL extension 'pluginUsages' to help determine actual version numbers used (deprecated, should later replace with versionInfo)
        project.extensions.create("pluginUsages", PluginUsages::class.java, target)
        
        // DSL extension 'versionInfo' to help determine actual version numbers used (of anything relevant for
        // re-constructing the build, e.g., system configuration, plugin versions, prerequisite versions, etc.).
        val versionInfoExtension = VersionInfo.defineExtension(target)
        
        // Task to create a wrapper 
        project.task<Wrapper>("createWrapper", {
            group = "Custom Gradle"
            description = "Creates a Gradle wrapper in the current directory using this instance of Gradle."
            val scriptFile = File(project.projectDir, "gradlew.bat")
            val gwFile = File(project.projectDir, "gw.bat")
            val holyGradleInitScriptFile = File(project.projectDir, "/gradle/init.d/holy-gradle-init.gradle")
            val initDir = File(project.projectDir, "/gradle/init.d/")
            jarFile = File(project.projectDir, "/gradle/wrapper/gradle-wrapper.jar")
            this.scriptFile = File(project.projectDir, "gradlew")
            doFirst {
                // If the user is running this task in a folder which already has a gradlew.bat, we don't want to
                // overwrite it, or Windows will do probably-unwanted/undefined things when Gradle exits and the rest
                // of the batch  file is to be executed.
                if (scriptFile.exists()) {
                    throw RuntimeException(
                        "You can only use this task in a sub-project which does not contain its own gradlew.bat"
                    )
                }

                if (holyGradleInitScriptFile.exists()) {
                    throw RuntimeException(
                        "You can only use this task in a sub-project which does not contain its own gradle/init.d/holy-gradle-init.gradle"
                    )
                }
            }
            doLast {
                CustomGradleCorePlugin::class.java.getResourceAsStream("/holygradle/gradlew.bat")
                        .copyTo(scriptFile.outputStream())
                CustomGradleCorePlugin::class.java.getResourceAsStream("/holygradle/gw.bat")
                        .copyTo(gwFile.outputStream())

                // Create Init Dir if it does not exist.
                FileHelper.ensureMkdirs(initDir, "to hold Holy Gradle init script")

                CustomGradleCorePlugin::class.java.getResourceAsStream("/holygradle/init.d/holy-gradle-init.gradle")
                        .copyTo(holyGradleInitScriptFile.outputStream())

                // We move the default ".properties" file to ".properties.in", removing the "distributionUrl=" line.
                // The "gradlew.bat" script will concatenate it with the distribution server URL (from the
                // HOLY_GRADLE_REPOSITORY_BASE_URL environment variable) and the rest of the distribution path (which
                // we write to a text file below).  This allows the same custom wrapper to be used from multiple sites
                // which don't share a single server for the distribution.
                val propertiesInputFile = File(this.propertiesFile.toString() + ".in")
                propertiesInputFile.printWriter().use { w ->
                    this.propertiesFile.forEachLine {
                        if (!it.startsWith("distributionUrl")) {
                            w.println(it)
                        }
                    }
                }
                Files.delete(this.propertiesFile.toPath())

                val distributionPathFile = File(project.projectDir, "/gradle/wrapper/distributionPath.txt")
                distributionPathFile.writeText("gradle-${project.gradle.gradleVersion}-bin.zip")
            }
        })
    
        if (project == project.rootProject) {
            // Create a task to allow user to ask for help 
            val helpUrl = "http://ediwiki/mediawiki/index.php/Gradle"
            project.task<Exec>("pluginHelp") {
                group = "Custom Gradle"
                description = "Opens the help page '${helpUrl}' in your favourite browser."
                commandLine("cmd", "/c", "start", helpUrl)
            }
            
            // Task to help the user to use 'gw' from any directory.
            project.task<DefaultTask>("doskey") {
                group = "Custom Gradle"
                description = "Helps you configure doskey to allow 'gw' to be used from any directory."
                doLast {
                    val doskeyFile = File("gwdoskey.bat")
                    doskeyFile.writeText(
                        "@echo off\r\n" +
                        "doskey gw=${project.gradle.gradleHomeDir.path}/bin/gradlew.bat \$*\r\n" +
                        "echo You can now use the command 'gw' from any directory for the lifetime of this command prompt."
                    )
                    println("-".repeat(80))
                    println("A batch file called '${doskeyFile.name}' has been created.")
                    println("Please invoke '${doskeyFile.name}' to set up the 'gw' doskey for this command prompt.")
                    println("-".repeat(80))
                }
            }
            
            // Task to print some version information.
            project.task<DefaultTask>("versionInfo") {
                group = "Custom Gradle"
                description = "Outputs version information about this instance of Gradle."
                doLast {
                    println("Gradle home: ")
                    println("  ${project.gradle.gradleHomeDir.path}\n")
                    println("Init script location: ")
                    println(" ${getInitScriptLocation(project)}\n")
                    val pluginsRepo = if (project.hasProperty("holyGradlePluginsRepository")) {
                        project.property("holyGradlePluginsRepository")
                    } else {
                        "NOT SET"
                    }
                    println("Plugin repository: ")
                    println("  ${pluginsRepo}\n")
                    println("Gradle properties location: ")
                    println("  ${gradlePropsFile.path}\n")
                    val customGradleVersion = versionInfoExtension.getVersion("custom-gradle")
                    val latestCustomGradle = VersionNumber.getLatestUsingBuildscriptRepositories(project, "holygradle", "custom-gradle")
                    print("Custom distribution version: ${customGradleVersion} (latest: $latestCustomGradle)")
                    println("Init script version: ${project.property("holyGradleInitScriptVersion")}")
                    println("Usage of Holy Gradle plugins:")
                    
                    // Print out version numbers for all holygradle buildscript dependencies.
                    // We print out:
                    //   component name: <actual version> (<requested version> <latest version>)
                    // e.g.
                    //   intrepid-plugin: 1.2.3.4 (requested: 1.2.3.+, latest: 1.2.7.7)
                    val versions = versionInfoExtension.buildscriptDependencies
                    versions.forEach { (version, requestedVersionStr) ->
                        if (version.group == "holygradle") {
                            val latest = VersionNumber.getLatestUsingBuildscriptRepositories(
                                project, version.group, version.name
                            )
                            println("    ${version.name}: ${version.version} (requested: $requestedVersionStr, latest: $latest)")
                        }
                    }   
                }
            }
            
            // Task to open all buildscripts in the workspace.
            project.task<DefaultTask>("openAllBuildscripts") {
                group = "Custom Gradle"
                description = "Opens all build-scripts in this workspace using the default program for '.gradle' files."
                doLast {
                    project.rootProject.allprojects.forEach { p ->
                        if (p.buildFile != null) {
                            project.exec {
                                it.commandLine("cmd", "/c", "start", p.buildFile.path)
                            }
                        }
                    }
                }
            }
            
            // Task to open the init script
            project.task<DefaultTask>("openInitScript") {
                group = "Custom Gradle"
                description = "Opens the init script using the default program for '.gradle' files."
                doLast {
                    val initScriptLocation = getInitScriptLocation(project)
                    if (initScriptLocation != null) {
                        project.exec {
                            it.commandLine("cmd", "/c", "start", initScriptLocation)
                        }
                    }
                }
            }
            
            // Task to open the user's global gradle.properties file.
            project.task<DefaultTask>("openGradleProperties") {
                group = "Custom Gradle"
                description = "Opens the user's system-wide gradle.properties file."
                doLast {
                    var homeDir = project.gradle.gradleHomeDir
                    while (homeDir != null && homeDir.parentFile != null && homeDir.name != ".gradle") {
                        homeDir = homeDir.parentFile
                    }
                    project.exec {
                        it.commandLine("cmd", "/c", "start", File(homeDir, "gradle.properties").path)
                    }
                }
            }
        }

        timer.endBlock()
    }
}

package holygradle.custom_gradle

import holygradle.custom_gradle.util.ProfilingHelper
import holygradle.custom_gradle.util.VersionNumber
import holygradle.io.FileHelper
import holygradle.kotlin.dsl.task
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.wrapper.Wrapper
import java.io.File

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
               
        prerequisites.specify("Java", "1.8").check()
               
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
                        "doskey gw=${project.gradle.gradleHomeDir?.path}/bin/gradlew.bat \$*\r\n" +
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
                    println("  ${project.gradle.gradleHomeDir?.path}\n")
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
                    println("Gradle version: ")
                    println("  ${project.gradle.gradleVersion}")
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
                        project.exec {
                            it.commandLine("cmd", "/c", "start", p.buildFile.path)
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

            project.gradle.addBuildListener(object : BuildListener {
                override fun buildFinished(result: BuildResult) {
                    if (BuildHelper.buildFailedDueToVersionConflict(result)) {
//2345678901234567890123456789012345678901234567890123456789012345678901234567890 <-- 80-column ruler
                        project.logger.error("""

Run the 'dependencies' task to see a tree of dependencies for each configuration
in your project(s).  Any dependency which has two versions separated by an arrow
("group:name:version1 -> version2") will cause a conflict.  The error message
for this build will only report one version conflict but your project may have
more, so check for any such arrows.

There are two main ways to resolve such a conflict.

1. Change the versions of some dependencies so that there is no conflict.  You
may have to do this by changing the versions of other dependencies nearer the
root of the dependency graph.

2. Move one conflicting dependency version to a different configuration.  You
may have to do this by changing the configurations of other dependencies nearer
the root of the dependency graph.  In that case you may also need to change the
target folder of that dependency, so that you are not trying to put two
different versions in the same location.

""")
                    }
                }
                override fun buildStarted(gradle: Gradle?) {}
                override fun projectsLoaded(gradle: Gradle?) {}
                override fun projectsEvaluated(gradle: Gradle?) {}
                override fun settingsEvaluated(settings: Settings?) {}
            })
        }

        timer.endBlock()
    }
}

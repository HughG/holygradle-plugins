package holygradle.custom_gradle

import holygradle.custom_gradle.util.ProfilingHelper
import holygradle.custom_gradle.util.VersionNumber
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.process.ExecSpec

import java.nio.file.Files

class CustomGradleCorePlugin implements Plugin<Project> {
    /**
     * Returns the path to the <tt>custom-gradle</tt> init script.
     * @param project The project to which this plugin is applied.
     * @return The path to the <tt>custom-gradle</tt> init script.
     */
    String getInitScriptLocation(Project project) {
        project.gradle.gradleHomeDir.path + "/init.d/holy-gradle-init.gradle"
    }
    
    void apply(Project project) {
        ProfilingHelper profilingHelper = new ProfilingHelper(project.logger)
        def timer = profilingHelper.startBlock("CustomGradleCorePlugin#apply(${project})")

        File gradlePropsFile = new File(project.gradle.gradleUserHomeDir, "gradle.properties")

        // DSL extension 'prerequisites' to allow build script to declare and verify prerequisites.
        PrerequisitesExtension prerequisites = PrerequisitesExtension.defineExtension(project)
               
        prerequisites.specify("Java", "1.7").check()
               
        // DSL extension 'pluginUsages' to help determine actual version numbers used (deprecated, should later replace with versionInfo)
        project.extensions.create("pluginUsages", PluginUsages, project)
        
        // DSL extension 'versionInfo' to help determine actual version numbers used (of anything relevant for
        // re-constructing the build, e.g., system configuration, plugin versions, prerequisite versions, etc.).
        VersionInfo versionInfoExtension = VersionInfo.defineExtension(project)
        
        // Task to create a wrapper 
        project.task("createWrapper", type: Wrapper) { Wrapper wrapper ->
            group = "Custom Gradle"
            description = "Creates a Gradle wrapper in the current directory using this instance of Gradle."
            final String customGradleVersion = project.holyGradleInitScriptVersion
            final File gwFile = new File(project.projectDir, "gw.bat")
            wrapper.gradleVersion = customGradleVersion
            wrapper.jarFile = new File(project.projectDir, "/gradle/gradle-wrapper.jar")
            wrapper.scriptFile = new File(project.projectDir, "gw")
            wrapper.doFirst {
                // If the user is running this task in a folder which already has a gw.bat, we don't want to overwrite
                // it, or Windows will do probably-unwanted/undefined things when Gradle exits and the rest of the batch
                // file is to be executed.
                if (gwFile.exists()) {
                    throw new RuntimeException(
                        "You can only use this task in a sub-project which does not contain its own gw.bat"
                    )
                }
            }
            wrapper.doLast {
                gwFile.withOutputStream { os ->
                    os << CustomGradleCorePlugin.class.getResourceAsStream("/holygradle/gw.bat")
                }

                // We move the default ".properties" file to ".properties.in", removing the "distributionUrl=" line.
                // The "gw.bat" script will concatenate it with the distribution server URL (from the
                // HOLY_GRADLE_REPOSITORY_BASE_URL environment variable) and the rest of the distribution path (which
                // we write to a text file below).  This allows the same custom wrapper to be used from multiple sites
                // which don't share a single server for the distribution.
                final File propertiesInputFile = new File(wrapper.propertiesFile.toString() + ".in")
                propertiesInputFile.withWriter { w ->
                    wrapper.propertiesFile.withInputStream { is ->
                        is.filterLine(w) { String line -> !line.startsWith("distributionUrl") }
                    }
                }
                Files.delete(wrapper.propertiesFile.toPath())

                File distributionPathFile = new File(project.projectDir, "/gradle/distributionPath.txt")
                distributionPathFile.text = "plugins-release/holygradle/custom-gradle/${project.holyGradleInitScriptVersion}/custom-gradle-${customGradleVersion}.zip"
            }
        }
    
        if (project == project.rootProject) {
            // Create a task to allow user to ask for help 
            String helpUrl = "http://ediwiki/mediawiki/index.php/Gradle"
            project.task("pluginHelp", type: Exec) { ExecSpec spec ->
                group = "Custom Gradle"
                description = "Opens the help page '${helpUrl}' in your favourite browser."
                spec.commandLine "cmd", "/c", "start", helpUrl
            }
            
            // Task to help the user to use 'gw' from any directory.
            project.task("doskey", type: DefaultTask) { DefaultTask task ->
                group = "Custom Gradle"
                description = "Helps you configure doskey to allow 'gw' to be used from any directory."
                task.doLast {
                    File doskeyFile = new File("gwdoskey.bat")
                    doskeyFile.write(
                        "@echo off\r\n" +
                        "doskey gw=${project.gradle.gradleHomeDir.path}/bin/gradle.bat \$*\r\n" +
                        "echo You can now use the command 'gw' from any directory for the lifetime of this command prompt."
                    )
                    println "-"*80
                    println "A batch file called '${doskeyFile.name}' has been created."
                    println "Please invoke '${doskeyFile.name}' to set up the 'gw' doskey for this command prompt."
                    println "-"*80
                }
            }
            
            // Task to print some version information.
            project.task("versionInfo", type: DefaultTask) { DefaultTask task ->
                group = "Custom Gradle"
                description = "Outputs version information about this instance of Gradle."
                task.doLast {
                    println "Gradle home: "
                    println "  ${project.gradle.gradleHomeDir.path}\n"
                    println "Init script location: "
                    println " ${getInitScriptLocation(project)}\n"
                    println "Plugin repository: "
                    println "  ${project.holyGradlePluginsRepository}\n"
                    println "Gradle properties location: "
                    println "  ${gradlePropsFile.path}\n"
                    int pad = 35
                    print "Custom distribution version: ".padRight(pad)
                    String latestCustomGradle = VersionNumber.getLatestUsingBuildscriptRepositories(project, "holygradle", "custom-gradle")
                    println versionInfoExtension.getVersion("custom-gradle") + " (latest: $latestCustomGradle)"
                    println "Init script version: ".padRight(pad) + project.holyGradleInitScriptVersion
                    println "Usage of Holy Gradle plugins:"
                    
                    // Print out version numbers for all holygradle buildscript dependencies.
                    // We print out:
                    //   component name: <actual version> (<requested version> <latest version>)
                    // e.g.
                    //   intrepid-plugin: 1.2.3.4 (requested: 1.2.3.+, latest: 1.2.7.7)
                    Map<ModuleVersionIdentifier, String> versions = versionInfoExtension.getBuildscriptDependencies()
                    versions.each { version, requestedVersionStr ->
                        if (version.getGroup() == "holygradle") {
                            String latest = VersionNumber.getLatestUsingBuildscriptRepositories(
                                project, version.getGroup(), version.getName()
                            )
                            println "    ${version.getName()}: ".padRight(pad) + version.getVersion() + " (requested: $requestedVersionStr, latest: $latest)"
                        }
                    }   
                }
            }
            
            // Task to open all buildscripts in the workspace.
            project.task("openAllBuildscripts", type: DefaultTask) { DefaultTask task ->
                group = "Custom Gradle"
                description = "Opens all build-scripts in this workspace using the default program for '.gradle' files."
                task.doLast {
                    project.rootProject.allprojects.each { p ->
                        if (p.buildFile != null) {
                            project.exec { ExecSpec spec ->
                                spec.commandLine "cmd", "/c", "start", p.buildFile.path
                            }
                        }
                    }
                }
            }
            
            // Task to open the init script
            project.task("openInitScript", type: DefaultTask) { DefaultTask task ->
                group = "Custom Gradle"
                description = "Opens the init script using the default program for '.gradle' files."
                task.doLast {
                    project.exec { ExecSpec spec ->
                        spec.commandLine "cmd", "/c", "start", getInitScriptLocation(project)
                    }
                }
            }
            
            // Task to open the user's global gradle.properties file.
            project.task("openGradleProperties", type: DefaultTask) { DefaultTask task ->
                group = "Custom Gradle"
                description = "Opens the user's system-wide gradle.properties file."
                task.doLast {
                    File homeDir = project.gradle.gradleHomeDir
                    while (homeDir != null && homeDir.parentFile != null && homeDir.name != ".gradle") {
                        homeDir = homeDir.parentFile
                    }
                    project.exec { ExecSpec spec ->
                        spec.commandLine "cmd", "/c", "start", new File(homeDir, "gradle.properties").path
                    }
                }
            }
        }

        timer.endBlock()
    }
}




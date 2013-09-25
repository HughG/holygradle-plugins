package holygradle.custom_gradle

import holygradle.custom_gradle.util.VersionNumber
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.process.ExecSpec

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
        File gradlePropsFile = new File(project.gradle.gradleUserHomeDir, "gradle.properties")
        
        // DSL extension to specify build dependencies
        project.extensions.buildDependencies = project.container(BuildDependency)

        // DSL extension 'taskDependencies' to allow build script to configure task dependencies
        // according to inter-project dependencies defined using 'buildDependencies'.
        project.extensions.create("taskDependencies", TaskDependenciesExtension, project)
        
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
            String customGradleVersion = project.gradle.gradleVersion + "-" + project.holyGradleInitScriptVersion
            wrapper.gradleVersion = customGradleVersion
            wrapper.distributionUrl = project.holyGradlePluginsRepository + "holygradle/custom-gradle/${project.holyGradleInitScriptVersion}/custom-gradle-${customGradleVersion}.zip"
            wrapper.jarFile = "${project.projectDir}/gradle/gradle-wrapper.jar"
            wrapper.scriptFile = "${project.projectDir}/gw"
            wrapper.doLast {
                String gwContent = """\
@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windowz variants

if not "%OS%" == "Windows_NT" goto win9xME_args
if "%@eval[2+2]" == "4" goto 4NT_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*
goto execute

:4NT_args
@rem Get arguments from the 4NT Shell from JP Software
set CMD_LINE_ARGS=%\$

:execute
@rem Setup the command line

set NO_DAEMON_OPTION=
FOR /f %%a IN ("%CMD_LINE_ARGS%") DO (
  if /i "%%a" == "fAD" set NO_DAEMON_OPTION=--no-daemon
  if /i "%%a" == "fetchAllDependencies" set NO_DAEMON_OPTION=--no-daemon
)

set CLASSPATH=%APP_HOME%\\gradle\\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS% %NO_DAEMON_OPTION%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="123456" goto execute
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
                """
                File gwFile = new File(project.projectDir, "gw.bat")
                gwFile.write(gwContent)
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
    }
}




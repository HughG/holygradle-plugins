package holygradle

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*
import org.gradle.api.tasks.wrapper.*
import org.gradle.api.logging.*

class CustomGradleCorePlugin implements Plugin<Project> {
    String getInitScriptLocation(Project project) {
        project.gradle.gradleHomeDir.path + "/init.d/holygradle-init.gradle"
    }
    
    void apply(Project project) {
        def gradlePropsFile = new File(project.gradle.gradleUserHomeDir, "gradle.properties")
        
        // DSL extension to specify build dependencies
        project.extensions.buildDependencies = project.container(BuildDependency)
        def buildDependencies = project.extensions.buildDependencies
        
        // DSL extension 'taskDependencies' to allow build script to configure task dependencies
        // according to inter-project dependencies defined using 'buildDependencies'.
        def taskDependenciesExtension = project.extensions.create("taskDependencies", TaskDependenciesExtension, project)
        
        // DSL extension 'prerequisites' to allow build script to declare and verify prerequisites.
        def prerequisitesExtension = project.extensions.create("prerequisites", PrerequisitesExtension, project)
        
        // DSL extension 'pluginUsages' to help determine actual version numbers used
        def pluginUsagesExtension = project.extensions.create("pluginUsages", PluginUsages, project)
        
        // Task to create a wrapper 
        project.task("createWrapper", type: Wrapper) {
            group = "Custom Gradle"
            description = "Creates a Gradle wrapper in the current directory using this instance of Gradle."
            def customGradleVersion = project.gradle.gradleVersion + "-" + project.ext.initScriptVersion
            gradleVersion customGradleVersion
            distributionUrl project.ext.holyGradlePluginsRepository + "holygradle/custom-gradle/${project.ext.initScriptVersion}/custom-gradle-${customGradleVersion}.zip"
            jarFile "${project.projectDir}/gradle/gradle-wrapper.jar"
            scriptFile "${project.projectDir}/gw"
            doLast {
                def gwContent = """\
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
                def gwFile = new File(project.projectDir, "gw.bat")
                gwFile.write(gwContent)
            }
        }
    
        if (project == project.rootProject) {
            // Create a task to allow user to ask for help 
            def helpUrl = "http://ediwiki/mediawiki/index.php/Gradle"
            project.task("pluginHelp", type: Exec) {
                group = "Custom Gradle"
                description = "Opens the help page '${helpUrl}' in your favourite browser."
                commandLine "cmd", "/c", "start", helpUrl
            }
            
            // Task to help the user to use 'gw' from any directory.
            project.task("doskey", type: DefaultTask) {
                group = "Custom Gradle"
                description = "Helps you configure doskey to allow 'gw' to be used from any directory."
                doLast {
                    def doskeyFile = new File("gwdoskey.bat")
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
            project.task("versionInfo", type: DefaultTask) {
                group = "Custom Gradle"
                description = "Outputs version information about this instance of Gradle."
                doLast {
                    def split = project.gradle.gradleHomeDir.parentFile.parentFile.name.split("-")
                    def customDistVersion = split[-1]
                    println "Gradle home: "
                    println "  ${project.gradle.gradleHomeDir.path}\n"
                    println "Init script location: "
                    println " ${getInitScriptLocation(project)}\n"
                    println "Plugin repository: "
                    println "  ${project.ext.holyGradlePluginsRepository}\n"
                    println "Gradle properties location: "
                    println "  ${gradlePropsFile.path}\n"
                    int pad = 30
                    println "Custom distribution version: ".padRight(pad) + customDistVersion
                    println "Init script version: ".padRight(pad) + project.ext.initScriptVersion
                    def plugins = pluginUsagesExtension.getMapping()
                    if (plugins.size() > 0) {
                        println "Usage of Green Gradle plugins:"
                        plugins.each { plugin, version ->
                            def p = "    ${plugin} : ".padRight(pad)
                            println "${p}${version}"
                        }
                    }            
                }
            }
            
            // Task to open all buildscripts in the workspace.
            project.task("openAllBuildscripts", type: DefaultTask) {
                group = "Custom Gradle"
                description = "Opens all build-scripts in this workspace using the default program for '.gradle' files."
                doLast {
                    project.rootProject.allprojects.each { p ->
                        if (p.buildFile != null) {
                            project.exec {
                                commandLine "cmd", "/c", "start", p.buildFile.path
                            }
                        }
                    }
                }
            }
            
            // Task to open the init script
            project.task("openInitScript", type: DefaultTask) {
                group = "Custom Gradle"
                description = "Opens the init script using the default program for '.gradle' files."
                doLast {
                    project.exec {
                        commandLine "cmd", "/c", "start", getInitScriptLocation(project)
                    }
                }
            }
            
            // Task to allow developer to upgrade to the most recent version of this init script.
            // TODO    
        }
    }
}




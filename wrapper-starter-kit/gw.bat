@rem First, self-upgrade, in case the createWrapper task created a new gw.bat, as gw.bat.new.
@if exist "%~dpnx0.new" (echo "Upgrading %~nx0 from %~nx0.new and re-running ..." & move /y "%~dpnx0.new" "%~dpnx0" & "%~dpnx0" %*)
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
@rem Get command-line arguments
set CMD_LINE_ARGS=%*

:execute
@rem Setup the command line

set NO_DAEMON_OPTION=
FOR /f %%a IN ("%CMD_LINE_ARGS%") DO (
  if /i "%%a" == "fAD" set NO_DAEMON_OPTION=--no-daemon
  if /i "%%a" == "fetchAllDependencies" set NO_DAEMON_OPTION=--no-daemon
)

if "x%HOLY_GRADLE_REPOSITORY_BASE_URL%"=="x" (
  echo You must set environment variable HOLY_GRADLE_REPOSITORY_BASE_URL
  echo to the base URL for the Holy Gradle distribution and plugins,
  echo for example, https://artifactory-server.my-corp.com/artifactory/
  goto fail
)

@rem Write the distribution base URL to a file and concat with the properties and path.
@rem Note that this "set" trick (to echo without a newline) sets ERRORLEVEL to 1.
if not "%HOLY_GRADLE_REPOSITORY_BASE_URL:~-1%"=="/" set HOLY_GRADLE_REPOSITORY_BASE_URL=%HOLY_GRADLE_REPOSITORY_BASE_URL%/
<nul set /p=distributionUrl=%HOLY_GRADLE_REPOSITORY_BASE_URL%> "%APP_HOME%\gradle\distributionUrlBase.txt"
if "%APP_HOME:~-21%"=="\wrapper-starter-kit\" (
  if "x%WRAPPER_STARTER_KIT_VERSION%"=="x" (
    echo To publish the wrapper-starter-kit you must set WRAPPER_STARTER_KIT_VERSION to
    echo the version of the custom-gradle distribution to use to publish the new wrapper.
    echo This may or may not be the same as the version you want to publish.
    goto fail
  ) else (
    <nul set /p=plugins-release/holygradle/custom-gradle/%WRAPPER_STARTER_KIT_VERSION%/custom-gradle-1.4-%WRAPPER_STARTER_KIT_VERSION%.zip> "%APP_HOME%\gradle\distributionPath.txt"
  )
)
copy >nul /y /a "%APP_HOME%\gradle\gradle-wrapper.properties.in"+"%APP_HOME%\gradle\distributionUrlBase.txt"+"%APP_HOME%\gradle\distributionPath.txt" "%APP_HOME%\gradle\gradle-wrapper.properties" /b

set CLASSPATH=%APP_HOME%\gradle\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS% %NO_DAEMON_OPTION%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="123456" goto execute
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
@rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
@rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
                
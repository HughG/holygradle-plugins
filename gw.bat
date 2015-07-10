@if "%DEBUG_GW%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Holy Gradle startup script for Windows, version [[GW_SCRIPT_VERSION]]
@rem  Published from [[GW_SCRIPT_SOURCE_VERSION]]
@rem
@rem  WARNING: Do not edit this script to customise your project, because it
@rem  may be replaced when upgrading to future versions of Gradle or Holy
@rem  Gradle.  Instead, customise within the build script, or in a script of
@rem  your own which calls this batch file.
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

if exist local_artifacts (
  echo %~nx0 found local_artifacts folder, so will NOT generate "%APP_HOME%\gradle\gradle-wrapper.properties".
  if not exist "%APP_HOME%\gradle\gradle-wrapper.properties" (
    echo ERROR: "%APP_HOME%\gradle\gradle-wrapper.properties" will not be generated but does not already exist.
    echo This may be because a zipped release you are using was created incorrectly.
  )
  @rem We have to use goto here, instead of an "else (...)", because Windows will try to parse the
  @rem "%HOLY_GRADLE_REPOSITORY_BASE_URL:~-1%" inside the else, and fail because the variable isn't set.
  goto wrapperPropertiesDone
)


@rem This "copy" makes sure that we use the most up-to-date list when *building* the plugins.
if exist %~dp0local\holy-gradle-plugins\base-url-lookup.txt (
  copy %~dp0local\holy-gradle-plugins\base-url-lookup.txt %~dp0gradle\base-url-lookup.txt
)
if "x%HOLY_GRADLE_REPOSITORY_BASE_URL%"=="x" (

  @rem Try to find a server URL based on the DNS suffix values on the local machine.
  if exist %~dp0gradle\base-url-lookup.txt (
    for /f "tokens=6" %%S in ('ipconfig ^| findstr "Connection-specific DNS Suffix"') do (
      for /f "eol=# tokens=1,2" %%T in (%~dp0gradle\base-url-lookup.txt) do (
        if "%%S"=="%%T" (
          echo In domain "%%S", defaulting HOLY_GRADLE_REPOSITORY_BASE_URL to "%%U".
          set HOLY_GRADLE_REPOSITORY_BASE_URL=%%U
          goto end_dns_search
        )
      )
    )
:end_dns_search
    @rem We need a comment here because a label must label a command, not a closing parenthesis.
  )

  if "x%HOLY_GRADLE_REPOSITORY_BASE_URL%"=="x" (
    echo You must set environment variable HOLY_GRADLE_REPOSITORY_BASE_URL
    echo to the base URL for the Holy Gradle distribution and plugins,
    echo for example, https://artifactory-server.my-corp.com/artifactory/
    goto fail
  )
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

:wrapperPropertiesDone
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

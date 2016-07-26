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

setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Allow use of Holy Gradle specific options to override system Java settings
if defined HOLY_GRADLE_JAVA_HOME set JAVA_HOME=%HOLY_GRADLE_JAVA_HOME%
if defined HOLY_GRADLE_GRADLE_HOME set GRADLE_HOME=%HOLY_GRADLE_GRADLE_HOME%

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

set DISTRIBUTION_ORIGINAL_PATH_FILE="%APP_HOME%gradle\distributionPath.txt"

@rem Find a local_artifacts sub-folder, in this folder or any ancestor folder.
set LOCAL_ARTIFACTS_DIR_NAME=local_artifacts
set LOCAL_ARTIFACTS_DIR_RELATIVE_URL=
set LOCAL_ARTIFACTS_DIR_PATH=
set "dir=%~f0"
:findLocalArtifactsLoop
  @rem Get the parent directory using ~dp trick, then strip the trailing '\'
  for %%d in ("%dir%") do set "dir=%%~dpd"
  set "dir=%dir:~0,-1%"

  if exist "%dir%\%LOCAL_ARTIFACTS_DIR_NAME%\" (
    set "LOCAL_ARTIFACTS_DIR_PATH=%dir%\%LOCAL_ARTIFACTS_DIR_NAME%"
    goto :findLocalArtifactsDone
  )

  if "%dir:~-1%" == ":" (
    goto :findLocalArtifactsDone
  )

  set "LOCAL_ARTIFACTS_DIR_RELATIVE_URL=../%LOCAL_ARTIFACTS_DIR_RELATIVE_URL%"
goto findLocalArtifactsLoop

:findLocalArtifactsDone



if not "x%LOCAL_ARTIFACTS_DIR_PATH%"=="x" (
  @rem We have to use goto here, instead of an "else (...)", because Windows will try to parse the
  @rem "%HOLY_GRADLE_REPOSITORY_BASE_URL:~-1%" inside the else, and fail because the variable isn't set.
  goto writeWrapperPropertiesForLocalArtifacts
)

@rem This "copy" makes sure that we use the most up-to-date list when *building* the plugins.
if exist "%~dp0local\holy-gradle-plugins\base-url-lookup.txt" (
  copy "%~dp0local\holy-gradle-plugins\base-url-lookup.txt" "%~dp0gradle\base-url-lookup.txt"
)
if "x%HOLY_GRADLE_REPOSITORY_BASE_URL%"=="x" (

  @rem Try to find a server URL based on the DNS suffix values on the local machine.
  if exist "%~dp0gradle\base-url-lookup.txt" (
    for /f "tokens=6" %%S in ('ipconfig /all ^| findstr "Connection-specific DNS Suffix"') do (
      for /f "eol=# tokens=1,2 usebackq" %%T in ("%~dp0gradle\base-url-lookup.txt") do (
        if "%%S"=="%%T" (
          echo In domain "%%S", defaulting HOLY_GRADLE_REPOSITORY_BASE_URL to "%%U".
          set HOLY_GRADLE_REPOSITORY_BASE_URL=%%U
          goto end_dns_search
        )
      )
    )
:end_dns_search
    ver >nul
    @rem We need a do-nothing command here because a label must label a command, not a closing parenthesis.
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
<nul set /p=distributionUrl=%HOLY_GRADLE_REPOSITORY_BASE_URL%> "%APP_HOME%gradle\distributionUrlBase.txt"
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
set DISTRIBUTION_PATH_FILE=%DISTRIBUTION_ORIGINAL_PATH_FILE%
goto writeWrapperProperties

:writeWrapperPropertiesForLocalArtifacts
<nul set /p=distributionUrl=../%LOCAL_ARTIFACTS_DIR_RELATIVE_URL%local_artifacts/> "%APP_HOME%gradle\distributionUrlBase.txt"
set DISTRIBUTION_LOCAL_PATH_FILE="%APP_HOME%gradle\distributionLocalPath.txt"
echo %~nx0 found "%LOCAL_ARTIFACTS_DIR_PATH%"
echo so will generate "%APP_HOME%gradle\gradle-wrapper.properties"
echo from %DISTRIBUTION_LOCAL_PATH_FILE%
echo instead of %DISTRIBUTION_ORIGINAL_PATH_FILE%.
set /p DISTRIBUTION_PATH= <%DISTRIBUTION_ORIGINAL_PATH_FILE%
set DISTRIBUTION_LOCAL_PATH=%DISTRIBUTION_PATH:plugins-release/holygradle/=%
<nul set /p=%DISTRIBUTION_LOCAL_PATH%>%DISTRIBUTION_LOCAL_PATH_FILE%

set DISTRIBUTION_PATH_FILE=%DISTRIBUTION_LOCAL_PATH_FILE%

:writeWrapperProperties
copy >nul /y /a "%APP_HOME%gradle\gradle-wrapper.properties.in"+"%APP_HOME%gradle\distributionUrlBase.txt"+%DISTRIBUTION_PATH_FILE% "%APP_HOME%\gradle\gradle-wrapper.properties" /b

@rem This "copy" makes sure that we use the most up-to-date list when *building* the plugins.
if exist "%~dp0local\holy-gradle-plugins\certs" (
  xcopy /i /s /y "%~dp0local\holy-gradle-plugins\certs" "%~dp0gradle\certs"
)
if not exist "%APP_HOME%gradle\certs" goto certsDone

@rem Find keytool.exe from java.exe.
for %%d in ("%JAVA_EXE%") do set "KEYTOOL_EXE=%%~dpd"
set KEYTOOL_EXE=%KEYTOOL_EXE%\keytool.exe

if exist "%KEYTOOL_EXE%" goto keytoolFound

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo A custom certificates directory was found at %APP_HOME%gradle\certs
echo and the Java keytool.exe tool is required to process it.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

:keytoolFound

set GW_CACERTS_PASS=gwcerts
if exist "%JAVA_HOME%\jre" (
    set "JRE_HOME=%JAVA_HOME%\jre"
) else (
    set "JRE_HOME=%JAVA_HOME%"
)
set JAVA_KEY_STORE="%JRE_HOME%\lib\security\cacerts"
set GW_KEY_STORE="%APP_HOME%gradle\cacerts"
set KEY_STORE_IMPORT_LOG="%APP_HOME%gradle\cacerts.import.log"
if exist %GW_KEY_STORE% del %GW_KEY_STORE%
>%KEY_STORE_IMPORT_LOG% 2>&1 "%KEYTOOL_EXE%" -importkeystore -srckeystore %JAVA_KEY_STORE% -srcstorepass changeit -destkeystore %GW_KEY_STORE% -deststorepass %GW_CACERTS_PASS%
if errorlevel 1 (
    echo Failed to import original Java keystore %JAVA_KEY_STORE%
    echo to local trust store %GW_KEY_STORE%.
    echo See %KEY_STORE_IMPORT_LOG% for details.
    goto fail
)
for %%C in ("%APP_HOME%gradle\certs\*.*") do (
    >>%KEY_STORE_IMPORT_LOG% 2>>&1 "%KEYTOOL_EXE%" -importcert -noprompt -file "%%C" -alias %%~nC -keystore %GW_KEY_STORE% -storepass %GW_CACERTS_PASS%
    if errorlevel 1 (
        echo Failed to import file "%%C"
        echo to local trust store %GW_KEY_STORE%.
        echo See %KEY_STORE_IMPORT_LOG% for details.
        goto fail
    )
)
set TRUST_STORE_OPTS="-Djavax.net.ssl.trustStore=%APP_HOME%gradle\cacerts" "-Djavax.net.ssl.trustStorePassword=%GW_CACERTS_PASS%"

:certsDone

set CLASSPATH=%APP_HOME%gradle\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% %TRUST_STORE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS% %NO_DAEMON_OPTION%

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
endlocal

:omega

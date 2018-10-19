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
setlocal EnableDelayedExpansion

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

SET GRADLE_DISTRIBUTION_URL=https\://services.gradle.org/distributions/

@rem Allow use of Holy Gradle specific options to override system Java settings
if defined HOLY_GRADLE_JAVA_8_HOME set JAVA_HOME=%HOLY_GRADLE_JAVA_8_HOME%
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

@rem This "copy" makes sure that we use the most up-to-date list when *building* the plugins.
@rem We need this file copied, even though the Holy Gradle build itself doesn't use it,
@rem because the wrapper-starter-kit subproject needs to copy it from here.
if exist "%~dp0local\holy-gradle-plugins\base-url-lookup.txt" (
  copy "%~dp0local\holy-gradle-plugins\base-url-lookup.txt" "%~dp0gradle\wrapper\base-url-lookup.txt"
)

@rem This "copy" makes sure that we use the most up-to-date list when *building* the plugins.
if exist "%~dp0local\holy-gradle-plugins\proxy-lookup.txt" (
  copy "%~dp0local\holy-gradle-plugins\proxy-lookup.txt" "%~dp0gradle\wrapper\proxy-lookup.txt"
)

@rem Try to find a proxy server and port based on the DNS suffix values on the local machine.
if exist "%~dp0gradle\wrapper\proxy-lookup.txt" (
  for /f "tokens=6" %%S in ('ipconfig ^| findstr "Connection-specific DNS Suffix"') do (
    for /f "eol=# tokens=1,2,3 usebackq" %%T in ("%~dp0gradle\wrapper\proxy-lookup.txt") do (
      if "%%S"=="%%T" (
        echo In domain "%%S", defaulting proxy server to "%%U" on port "%%V".
        set HOLY_GRADLE_PROXY_OPTS=-Dhttp.proxyHost=%%U -Dhttp.proxyPort=%%V -Dhttps.proxyHost=%%U -Dhttps.proxyPort=%%V
        goto proxySearchDone
      )
    )
  )
:proxySearchDone
  echo >nul
  @rem We need a do-nothing command above because a label must label a command, not a closing parenthesis.
)

set INIT_SCRIPT_OPTS=
FOR %%f IN ("%APP_HOME%gradle\init.d\*.gradle") DO (
    SET "INIT_SCRIPT_OPTS=!INIT_SCRIPT_OPTS! -I "%%f""
)

if "x%HOLY_GRADLE_REPOSITORY_BASE_URL%"=="x" (

  @rem Try to find a server URL based on the DNS suffix values on the local machine.
  if exist "%~dp0gradle\wrapper\base-url-lookup.txt" (
    for /f "tokens=6" %%S in ('ipconfig /all ^| findstr "Connection-specific DNS Suffix"') do (
      for /f "eol=# tokens=1,2 usebackq" %%T in ("%~dp0gradle\wrapper\base-url-lookup.txt") do (
        if "%%S"=="%%T" (
          echo In domain "%%S", defaulting HOLY_GRADLE_REPOSITORY_BASE_URL to "%%U".
          set HOLY_GRADLE_REPOSITORY_BASE_URL=%%U
          goto dnsSearchDone
        )
      )
    )
:dnsSearchDone
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


@rem This "copy" makes sure that we use the most up-to-date list when *building* the plugins.
if exist "%~dp0local\holy-gradle-plugins\certs" (
  xcopy /i /s /y "%~dp0local\holy-gradle-plugins\certs" "%~dp0gradle\wrapper\certs"
)
if not exist "%APP_HOME%gradle\wrapper\certs" goto certsDone
@rem Find keytool.exe from java.exe.

for %%d in ("%JAVA_EXE%") do set "KEYTOOL_EXE=%%~dpd"
set KEYTOOL_EXE=%KEYTOOL_EXE%\keytool.exe

if exist "%KEYTOOL_EXE%" goto keytoolFound

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo A custom certificates directory was found at %APP_HOME%gradle\wrapper\certs
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
set GW_KEY_STORE="%APP_HOME%gradle\wrapper\cacerts"
set KEY_STORE_IMPORT_LOG="%APP_HOME%gradle\wrapper\cacerts.import.log"
if exist %GW_KEY_STORE% del %GW_KEY_STORE%
>%KEY_STORE_IMPORT_LOG% 2>&1 "%KEYTOOL_EXE%" -importkeystore -srckeystore %JAVA_KEY_STORE% -srcstorepass changeit -destkeystore %GW_KEY_STORE% -deststorepass %GW_CACERTS_PASS%
if errorlevel 1 (
    echo Failed to import original Java keystore %JAVA_KEY_STORE%
    echo to local trust store %GW_KEY_STORE%.
    echo See %KEY_STORE_IMPORT_LOG% for details.
    goto fail
)
for %%C in ("%APP_HOME%gradle\wrapper\certs\*.*") do (
    >>%KEY_STORE_IMPORT_LOG% 2>>&1 "%KEYTOOL_EXE%" -importcert -noprompt -file "%%C" -alias %%~nC -keystore %GW_KEY_STORE% -storepass %GW_CACERTS_PASS%
    if errorlevel 1 (
        echo Failed to import file "%%C"
        echo to local trust store %GW_KEY_STORE%.
        echo See %KEY_STORE_IMPORT_LOG% for details.
        goto fail
    )
)
set TRUST_STORE_OPTS="-Djavax.net.ssl.trustStore=%APP_HOME%gradle\wrapper\cacerts" "-Djavax.net.ssl.trustStorePassword=%GW_CACERTS_PASS%"

:certsDone

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% %HOLY_GRADLE_PROXY_OPTS% %TRUST_STORE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS% %NO_DAEMON_OPTION% %INIT_SCRIPT_OPTS% %HOLY_GRADLE_OPTS%

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

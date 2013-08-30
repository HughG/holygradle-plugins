@echo off

if x%1x==xx (
    echo Usage: %~nx0 source.asciidoc
    goto :eof
)
REM You can drop the AsciiDoc file onto this batch file in Windows Explorer, too.

setlocal
set CYGHOME=c:\cygwin64\bin

REM Parse everything except the last argument into ASCIIDOC_OPTIONS.
set ASCIIDOC_OPTIONS=
:opt_loop
if x%2==x (
    goto :end_opt_loop
) else (
    set ASCIIDOC_OPTIONS=%ASCIIDOC_OPTIONS% %1
)
shift
goto :opt_loop
:end_opt_loop

REM Use cygpath to translate the path of the AsciiDoc file into Cygwin format.
REM Using "%~dp1" and "%~nx1" ensures paths are absolute, not relative.
for /f "usebackq" %%D in (`%CYGHOME%\cygpath.exe %~dp1`) do %CYGHOME%\bash.exe -l -c "cd %%D ; asciidoc %ASCIIDOC_OPTIONS% %~nx1"

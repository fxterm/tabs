@echo off
rem Closes the running window the normal way (it saves every tab, and asks first if a claude is still working),
rem puts the newly built jar in place, and starts it again. Run it from outside the window (Win+R or another
rem terminal): a shell inside one of its tabs ends when the window closes.
taskkill /im fxterm-tabs.exe >nul 2>&1
:wait
tasklist /fi "imagename eq fxterm-tabs.exe" | find /i "fxterm-tabs.exe" >nul && (timeout /t 1 /nobreak >nul & goto wait)
rem An optional first argument names the jar to put in place (a release's, say); the default is the newest build.
set "NEWJAR=%~1"
if not defined NEWJAR set "NEWJAR=%~dp0shade\fxterm-tabs.jar"
copy /y "%NEWJAR%" "%~dp0dist\fxterm-tabs.jar" >nul
rem The exe the build made (it runs dist\fxterm-tabs.jar and keeps the update url, so a real newer release is one click
rem in the footer) replaces dist's. Without it, an exe that one-click update swapped for a release exe stays a release exe.
if exist "%~dp0app\target\jr\fxterm-tabs.exe" copy /y "%~dp0app\target\jr\fxterm-tabs.exe" "%~dp0dist\fxterm-tabs.exe" >nul
start "" "%~dp0dist\fxterm-tabs.exe"

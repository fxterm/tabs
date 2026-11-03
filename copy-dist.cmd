@echo off
rem Copies the latest build to dist. A running window reads its classes and resources from dist\fxterm-tabs.jar as it goes,
rem so replacing that file under it makes every later read fail (PRP 15: 339,221 "Error loading D3D shader object", each caused
rem by "ZipFile invalid LOC header"). So this refuses while any Tabs is running. Use restart.cmd or swap.ps1 to replace it safely.
tasklist /fi "imagename eq fxterm-tabs.exe" | find /i "fxterm-tabs.exe" >nul && goto running
tasklist /fi "imagename eq tabs.exe" | find /i "tabs.exe" >nul && goto running
copy /y "%~dp0shade\fxterm-tabs.jar" "%~dp0dist\fxterm-tabs.jar"
exit /b %errorlevel%
:running
echo Not copied: Tabs is running from dist\fxterm-tabs.jar, and replacing it under the window breaks the window.
echo Close Tabs first, or use restart.cmd / swap.ps1, which close it, replace the jar and start it again.
exit /b 1

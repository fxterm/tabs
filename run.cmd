@echo off
rem The copy to use: dist\fxterm-tabs.exe runs dist\fxterm-tabs.jar, which only copy-dist.cmd replaces (never a test build).
rem Errors: %USERPROFILE%\fxterm-tabs\fxterm-tabs\fxterm-tabs.log
start "" "%~dp0dist\fxterm-tabs.exe" %*

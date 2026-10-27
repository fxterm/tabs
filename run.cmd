@echo off
rem The copy to use: dist\tabbyfx.exe runs dist\tabbyfx.jar, which only copy-dist.cmd replaces (never a test build).
rem Errors: %USERPROFILE%\tabbyfx\tabbyfx\tabbyfx.log
start "" "%~dp0dist\tabbyfx.exe" %*

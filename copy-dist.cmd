@echo off
rem Copies the latest build to dist. Close tabbyfx first: a running window must not have its jar replaced.
copy /y "%~dp0shade\tabbyfx.jar" "%~dp0dist\tabbyfx.jar"

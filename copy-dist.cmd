@echo off
rem Copies the latest build to dist. Close fxterm-tabs first: a running window must not have its jar replaced.
copy /y "%~dp0shade\fxterm-tabs.jar" "%~dp0dist\fxterm-tabs.jar"

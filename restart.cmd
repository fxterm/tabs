@echo off
rem Closes the running window the normal way (it saves every tab, and asks first if a claude is still working),
rem puts the newly built jar in place, and starts it again. Run it from outside the window (Win+R or another
rem terminal): a shell inside one of its tabs ends when the window closes.
taskkill /im tabbyfx.exe >nul 2>&1
:wait
tasklist /fi "imagename eq tabbyfx.exe" | find /i "tabbyfx.exe" >nul && (timeout /t 1 /nobreak >nul & goto wait)
copy /y "%~dp0shade\tabbyfx.jar" "%~dp0dist\tabbyfx.jar" >nul
start "" "%~dp0dist\tabbyfx.exe"

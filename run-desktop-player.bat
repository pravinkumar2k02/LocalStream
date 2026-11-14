@echo off
setlocal
echo ========================================================
echo  Starting Local Stream Player (Laptop Client)
echo ========================================================
set "JAVA_HOME=C:\Users\p.k.palanisamy\.jdks\jbr-21.0.11"
set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%~dp0DesktopStreamPlayer"
call .\gradlew.bat run
pause

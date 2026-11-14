@echo off
setlocal
echo ========================================================
echo  Starting Local Stream Player (Python Client)
echo ========================================================

cd /d "%~dp0PythonStreamPlayer"

where python >nul 2>nul
if %errorlevel% neq 0 (
    set "PYTHON_EXE=%LOCALAPPDATA%\Programs\Python\Python314\python.exe"
) else (
    set "PYTHON_EXE=python"
)

"%PYTHON_EXE%" main.py
if %errorlevel% neq 0 (
    echo.
    echo If any dependencies are missing, installing requirements...
    "%PYTHON_EXE%" -m pip install -r requirements.txt
    "%PYTHON_EXE%" main.py
)
pause

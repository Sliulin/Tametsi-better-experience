@echo off
setlocal
cd /d "%~dp0"
set "modjar=mods\tametsi-mod.jar"
if exist "mods\active-runtime.txt" set /p modversion=<"mods\active-runtime.txt"
if defined modversion set "modjar=mods\%modversion%"
if not exist "%modjar%" (
    echo Mod package missing. Run mods\build.ps1 first.
    pause
    exit /b 1
)
start "" "jre\bin\javaw.exe" -cp "%modjar%;mods\lib\asm-9.7.1.jar;tametsi.exe" tametsimod.Manager

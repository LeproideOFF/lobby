@echo off
setlocal enabledelayedexpansion
TITLE Forgium Lobbies - Single Instance

:: Configuration
SET JAR_PATH=target\lobby-1.0.jar

echo ========================================
echo   Forgium Lobbies - Single Instance
echo ========================================
echo.

if not exist "%JAR_PATH%" (
    echo [ERREUR] Le JAR n'existe pas. Veuillez lancer start_lobbies.bat d'abord.
    pause
    exit /b
)

set /p LOBBY_ID="Entrez l'ID du lobby (ex: 1): "
if "%LOBBY_ID%"=="" set LOBBY_ID=1

echo Lancement du Lobby #%LOBBY_ID%...
java -Xms128M -Xmx128M -XX:+UseSerialGC -Dminestom.chunk-view-distance=4 -Dlobby.id=%LOBBY_ID% -jar "%JAR_PATH%"

pause

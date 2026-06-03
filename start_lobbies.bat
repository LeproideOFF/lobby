@echo off
setlocal enabledelayedexpansion
TITLE Forgium Lobbies - Manager

:: Configuration
SET JAR_PATH=target\lobby-1.0.jar
SET MVN_VERSION=3.9.6
SET MVN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VERSION%/apache-maven-%MVN_VERSION%-bin.zip
SET PORTABLE_MVN_DIR=.mvn_portable

echo ========================================
echo   Forgium Lobbies - Windows Launcher
echo ========================================
echo.

:: 1. Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERREUR] Java n'est pas installe ou n'est pas dans votre PATH.
    echo Veuillez installer Java 17 ou plus : https://adoptium.net/
    pause
    exit /b
)

:: 2. Check/Build JAR
if not exist "%JAR_PATH%" (
    echo [INFO] Le fichier %JAR_PATH% est absent.
    echo [INFO] Tentative de compilation automatique...
    echo.

    :: Check for system Maven
    mvn -version >nul 2>&1
    if %errorlevel% eq 0 (
        echo [INFO] Utilisation de Maven installe sur le systeme...
        call mvn package -DskipTests
    ) else (
        echo [INFO] Maven n'est pas installe. Preparation d'un Maven portable...
        if not exist "%PORTABLE_MVN_DIR%" (
            echo [INFO] Telechargement de Maven %MVN_VERSION%...
            powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%MVN_URL%' -OutFile 'mvn.zip'"
            if %errorlevel% neq 0 (
                echo [ERREUR] Echec du telechargement de Maven. Verifiez votre connexion internet.
                pause
                exit /b
            )
            echo [INFO] Extraction de Maven...
            powershell -Command "Expand-Archive -Path 'mvn.zip' -DestinationPath '%PORTABLE_MVN_DIR%'"
            del mvn.zip
        )
        
        for /d %%i in ("%PORTABLE_MVN_DIR%\apache-maven-*") do set MVN_BIN_DIR=%%~fi\bin
        set "PATH=!MVN_BIN_DIR!;%PATH%"
        
        echo [INFO] Compilation via Maven portable...
        call "!MVN_BIN_DIR!\mvn.cmd" package -DskipTests
    )
)

if not exist "%JAR_PATH%" (
    echo.
    echo [ERREUR] La compilation a echoue ou le JAR n'a pas pu etre genere.
    echo Verifiez les erreurs ci-dessus.
    pause
    exit /b
)

echo.
echo [SUCCES] Le JAR est pret. Lancement des 5 lobbys...
echo.

:: 3. Launch Lobbies
for /L %%i in (1,1,5) do (
    echo Démarrage du Lobby #%%i...
    SET LOBBY_ID=%%i
    :: On utilise START pour lancer chaque lobby dans une fenetre separee (minimisee)
    start "Lobby %%i" /min java -Xms64M -Xmx64M -XX:+UseSerialGC -Dminestom.chunk-view-distance=2 -Dlobby.id=%%i -jar "%JAR_PATH%"
    timeout /t 1 /nobreak > nul
)

echo.
echo ========================================
echo   Les 5 lobbys ont ete lances !
echo   Ports : 25570 a 25574
echo.
echo   Utilisez stop_lobbies.bat pour arreter.
echo ========================================
pause

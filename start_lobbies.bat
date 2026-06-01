@echo off
TITLE Forgium Lobbies - Start
echo Lancement des 5 lobbys Forgium (v1.0)...

:: Lancement du Lobby 1
echo Démarrage du Lobby #1 (Port 25570)...
SET LOBBY_ID=1
start "Lobby 1" /min java -Xms48M -Xmx48M -XX:+UseSerialGC -Dminestom.chunk-view-distance=2 -jar target/lobby-1.0.jar

timeout /t 1 /nobreak > nul

:: Lancement du Lobby 2
echo Démarrage du Lobby #2 (Port 25571)...
SET LOBBY_ID=2
start "Lobby 2" /min java -Xms48M -Xmx48M -XX:+UseSerialGC -Dminestom.chunk-view-distance=2 -jar target/lobby-1.0.jar

timeout /t 1 /nobreak > nul

:: Lancement du Lobby 3
echo Démarrage du Lobby #3 (Port 25572)...
SET LOBBY_ID=3
start "Lobby 3" /min java -Xms48M -Xmx48M -XX:+UseSerialGC -Dminestom.chunk-view-distance=2 -jar target/lobby-1.0.jar

timeout /t 1 /nobreak > nul

:: Lancement du Lobby 4
echo Démarrage du Lobby #4 (Port 25573)...
SET LOBBY_ID=4
start "Lobby 4" /min java -Xms48M -Xmx48M -XX:+UseSerialGC -Dminestom.chunk-view-distance=2 -jar target/lobby-1.0.jar

timeout /t 1 /nobreak > nul

:: Lancement du Lobby 5
echo Démarrage du Lobby #5 (Port 25574)...
SET LOBBY_ID=5
start "Lobby 5" /min java -Xms48M -Xmx48M -XX:+UseSerialGC -Dminestom.chunk-view-distance=2 -jar target/lobby-1.0.jar

echo.
echo Les 5 lobbys ont ete lances dans des fenetres reduites.
echo Ports : 25570 a 25574
pause

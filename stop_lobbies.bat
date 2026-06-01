@echo off
echo Arret de tous les processus Java (Lobbys Forgium)...
taskkill /F /FI "WINDOWTITLE eq Lobby *"
echo.
echo Tous les lobbys ont ete arretes.
pause

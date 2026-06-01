#!/bin/bash
echo "Lancement des 5 lobbys Forgium (v1.0)..."

for i in {1..5}
do
   echo "Démarrage du Lobby #$i..."
   LOBBY_ID=$i nohup java -Xms48M -Xmx48M -XX:+UseSerialGC -Dminestom.chunk-view-distance=2 -jar target/lobby-1.0.jar > lobby_$i.log 2>&1 &
   sleep 1
done

echo "Les 5 lobbys ont été lancés en arrière-plan."
echo "Ports utilisés : 25570 à 25574"
echo "Utilisez ./stop_lobbies.sh pour tout arrêter."

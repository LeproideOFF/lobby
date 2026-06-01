#!/bin/bash
echo "Lancement des 5 lobbys Forgium (v0.9)..."

for i in {1..5}
do
   echo "Démarrage du Lobby #$i..."
   LOBBY_ID=$i nohup mvn exec:exec > lobby_$i.log 2>&1 &
   sleep 2
done

echo "Les 5 lobbys ont été lancés en arrière-plan."
echo "Ports utilisés : 25570, 25571, 25572, 25573, 25574"
echo "Utilisez ./stop_lobbies.sh pour tout arrêter."

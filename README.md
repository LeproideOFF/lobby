# Minestom Lobby Server

Une base de serveur Minecraft simple utilisant [Minestom](https://github.com/Minestom/Minestom).

## Prérequis

- Java 21+ (Java 26 recommandé)
- Maven

## Lancement

1. Compilez le projet :
   ```bash
   mvn compile
   ```

2. Lancez le serveur :
   ```bash
   mvn exec:java
   ```

Le serveur sera accessible sur `localhost:25565` (en mode offline par défaut).

## Fonctionnalités incluses

- Monde plat (Grass blocks jusqu'à y=40)
- Téléportation automatique au spawn lors de la connexion
- Message de bienvenue

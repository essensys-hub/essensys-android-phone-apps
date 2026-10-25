## Purpose

Permet de régler depuis l'app le temps de course de chaque volet et store, comme la carte « Temps de course » de la page Volets du portail.

## ADDED Requirements

### Requirement: Réglage du temps de course
L'écran Volets SHALL proposer, pour chaque volet et store, le réglage de son temps de course en secondes, identique à l'ouverture et à la fermeture. L'app SHALL envoyer une injection unique `k = indice du temps de course du volet (566–589), v = "<secondes>"`, avec les mêmes indices que le portail (`ShuttersPage.tsx`). Ces couples SHALL être verrouillés par des tests de non-régression alignés sur les deux apps.

#### Scenario: Volet 1 Salon à 30 s
- **WHEN** l'utilisateur règle « Volet 1 Salon » à 30 secondes puis enregistre
- **THEN** l'app envoie l'injection `k=566, v="30"`

### Requirement: Plage autorisée
La valeur SHALL être un entier de 1 à 255. Une valeur vide, nulle, supérieure à 255 ou non numérique NE SHALL PAS être envoyée, et l'app SHALL indiquer « Entre 1 et 255 secondes ».

#### Scenario: Valeur hors plage
- **WHEN** l'utilisateur saisit 300 pour un volet
- **THEN** aucune requête n'est envoyée et l'app affiche « Entre 1 et 255 secondes »

### Requirement: Valeur actuelle
L'app SHALL afficher la valeur actuelle lue dans la table d'échange (« Actuel : 25 s »). Si elle n'est pas connue, l'app SHALL afficher « Non transmis (défaut usine : 120 s, store : 255 s) ». Après un envoi réussi, la valeur affichée SHALL devenir la valeur envoyée.

#### Scenario: Après enregistrement
- **WHEN** l'enregistrement de 30 s réussit pour « Volet 1 Salon »
- **THEN** l'écran affiche « Actuel : 30 s » pour ce volet

### Requirement: Commandes désactivées
L'enregistrement SHALL être désactivé quand l'armoire est hors ligne, avec le même anti-rebond, le même retour et le même mode test que les autres commandes.

#### Scenario: Armoire hors ligne
- **WHEN** l'armoire est hors ligne
- **THEN** le bouton d'enregistrement du temps de course est désactivé

## Purpose

Permet de choisir depuis l'app le mode du chauffe-eau et de l'arrosage, avec les indices et les valeurs de la table d'échange du firmware.

## ADDED Requirements

### Requirement: Indices du firmware
Les commandes du chauffe-eau et de l'arrosage SHALL utiliser les indices de `TableEchange.h` : `Cumulus_Mode` = 353 et `Arrose_Mode` = 363. Les indices 400–402 et 500–504 utilisés par le portail NE SHALL PAS être envoyés. Ces couples SHALL être verrouillés par des tests de non-régression.

#### Scenario: Aucun indice du portail
- **WHEN** les tests de non-régression parcourent les commandes du chauffe-eau et de l'arrosage
- **THEN** aucune ne vise un indice entre 400 et 402 ou entre 500 et 504

### Requirement: Chauffe-eau
L'écran Chauffe-eau SHALL proposer trois modes, chacun envoyé en une injection sur l'indice 353 : « Autonome (marche permanente) » = 0, « Heures creuses » = 1, « Arrêt » = 2.

#### Scenario: Heures creuses
- **WHEN** l'utilisateur choisit « Heures creuses »
- **THEN** l'app envoie `k=353, v="1"`

### Requirement: Arrosage
L'écran Arrosage SHALL proposer « Automatique » = 255, « 15 minutes » = 15, « 30 minutes » = 30, « 60 minutes » = 60 et « Arrêt » = 0, chacun envoyé en une injection sur l'indice 363.

#### Scenario: Arrosage 30 minutes
- **WHEN** l'utilisateur choisit « 30 minutes »
- **THEN** l'app envoie `k=363, v="30"`

### Requirement: Mode actuel
Quand la lecture `exchange` des indices 353 et 363 renvoie une valeur, l'app SHALL indiquer le mode actuel. Sinon, elle SHALL afficher « Mode actuel inconnu » sans bloquer les commandes.

#### Scenario: Valeur inconnue
- **WHEN** la lecture ne renvoie pas l'indice 363
- **THEN** l'écran Arrosage affiche « Mode actuel inconnu » et les boutons restent actifs

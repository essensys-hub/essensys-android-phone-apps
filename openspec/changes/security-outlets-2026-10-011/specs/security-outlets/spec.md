## Purpose

Permet de couper et de rétablir les prises de sécurité depuis l'app Android, et d'afficher si elles sont coupées.

## ADDED Requirements

### Requirement: Commande des prises de sécurité
L'app SHALL proposer « Couper » et « Rétablir » les prises de sécurité. Chaque commande SHALL être envoyée en un seul lot `[{k: 590, v: "1"}, {k: 623, v: "1"}]` pour couper et `[{k: 590, v: "1"}, {k: 623, v: "2"}]` pour rétablir (cloud : `POST /api/portal/inject/batch` ; LAN : `POST /api/admin/inject` avec un tableau). Ces couples SHALL être verrouillés par un test de non-régression `NR-android-18`.

#### Scenario: Couper
- **WHEN** l'utilisateur confirme « Couper les prises de sécurité »
- **THEN** le lot envoyé contient `590 = "1"` et `623 = "1"`, et rien d'autre

#### Scenario: Rétablir
- **WHEN** l'utilisateur choisit « Rétablir les prises de sécurité »
- **THEN** le lot envoyé contient `590 = "1"` et `623 = "2"`

### Requirement: Confirmation et disponibilité
« Couper » SHALL demander une confirmation explicite ; une annulation NE SHALL envoyer aucune requête. Les deux commandes SHALL être désactivées quand l'armoire est hors ligne, SHALL avoir un anti-rebond (une commande en vol à la fois) et SHALL porter l'en-tête dry-run en mode test.

#### Scenario: Annulation
- **WHEN** l'utilisateur choisit « Couper » puis annule la confirmation
- **THEN** aucune requête n'est envoyée

#### Scenario: Armoire hors ligne
- **WHEN** l'armoire est hors ligne
- **THEN** « Couper » et « Rétablir » sont désactivés

### Requirement: État des prises
L'app SHALL lire l'indice 440 dans la table d'échange et afficher « Prises coupées » (440 = 1), « Prises pilotées » (440 = 0) ou « État inconnu » (absent ou autre valeur). Après une commande réussie, l'état SHALL être relu après quelques secondes ; tant que la nouvelle valeur n'est pas reçue, l'affichage SHALL indiquer « En attente de l'armoire ».

#### Scenario: État inconnu
- **WHEN** la lecture ne renvoie pas l'indice 440
- **THEN** l'écran affiche « État inconnu » et les commandes restent utilisables

### Requirement: Prises de sécurité dans un scénario
L'éditeur de scénarios SHALL proposer, pour chaque scénario modifiable, le choix « Prises de sécurité : ne rien faire / couper / rétablir », enregistré au décalage 31 du bloc du scénario (`592 + (n − 1) × 41 + 31`) avec les valeurs 0, 1 et 2.

#### Scenario: « Je sors » coupe les prises
- **WHEN** l'utilisateur choisit « couper » pour le scénario 2 puis enregistre
- **THEN** l'indice 664 (633 + 31) est enregistré à « 1 » avec le reste de la définition du scénario

## Purpose

Permet de lancer depuis l'app les scénarios de l'armoire (Je sors, vacances, Je rentre, coucher, lever, personnalisés), de les remettre aux réglages d'usine et d'éditer les scénarios modifiables.

## ADDED Requirements

### Requirement: Liste des scénarios
L'app SHALL afficher les scénarios 2 à 8 renvoyés par le backend (cloud : `GET /api/portal/scenarios` ; LAN : `GET /api/scenarios`) avec leur libellé, et SHALL signaler le dernier scénario lancé. Le scénario 1 (réservé au serveur) NE SHALL PAS être affiché.

#### Scenario: Affichage
- **WHEN** l'utilisateur ouvre l'écran Scénarios
- **THEN** il voit « Je sors », « Je pars en vacances », « Je rentre », « Je vais me coucher », « Je me lève », « Personnalisé 1 » et « Personnalisé 2 », et le dernier lancé est marqué

### Requirement: Lancement
« Lancer » SHALL appeler `POST /api/portal/scenarios/{n}/launch` en cloud ou `POST /api/scenarios/{n}/launch` en LAN, avec le même retour et le même anti-rebond que les autres commandes. Le lancement SHALL être désactivé quand l'armoire est hors ligne.

#### Scenario: Je sors
- **WHEN** l'utilisateur lance « Je sors »
- **THEN** l'app appelle le lancement du scénario 2 et affiche « Commande envoyée »

### Requirement: Retour aux réglages d'usine
Pour les scénarios 2 à 6, l'app SHALL proposer « Réglages d'usine », avec une confirmation explicite, qui appelle `POST …/scenarios/{n}/restore`. Cette action NE SHALL PAS être proposée pour les scénarios 7 et 8.

#### Scenario: Confirmation exigée
- **WHEN** l'utilisateur choisit « Réglages d'usine » pour « Je rentre » puis annule
- **THEN** aucune requête n'est envoyée

### Requirement: Édition
Pour un scénario modifiable, l'app SHALL lire sa définition (`GET …/scenarios/{n}`) et les métadonnées des masques (`GET …/scenarios/meta/bitmasks`), présenter les lumières et volets comme des cases à cocher, puis enregistrer par `PUT …/scenarios/{n}` `{params:{"<indice>":"<0..255>"}}`. Les métadonnées décrivant les indices du scénario 1 (605, 606, 607, 613, 617, 620), l'app SHALL les transposer dans le bloc du scénario édité : `indice = 592 + (n − 1) × 41 + (indice_méta − 592)`. Tous les indices envoyés SHALL appartenir au bloc du scénario.

#### Scenario: Cocher une lumière dans « Je rentre »
- **WHEN** l'utilisateur coche une lumière décrite à l'indice 605 dans le scénario 4
- **THEN** la valeur modifiée est envoyée à l'indice 728 (bloc 715–755), jamais à 605

### Requirement: Mode test
En mode test, le lancement, la remise aux réglages d'usine et l'enregistrement SHALL porter l'en-tête dry-run, en cloud comme en LAN.

#### Scenario: Lancer en mode test
- **WHEN** le mode test est actif et l'utilisateur lance « Je me lève »
- **THEN** la requête porte `X-Essensys-Test-Mode: dry-run` et l'app affiche « Commande validée (test, non exécutée) »

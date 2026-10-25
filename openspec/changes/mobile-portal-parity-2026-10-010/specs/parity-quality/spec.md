## Purpose

Garantit que les nouvelles fonctions sont testées sans armoire réelle, identiques sur Android et iPhone, vérifiées par `/checkup` et livrées aux testeurs en version 2.1.0.

## ADDED Requirements

### Requirement: Tests unitaires
Chaque nouvelle fonction SHALL avoir des tests unitaires contre un serveur simulé : codec du planning et découpage en lots, transposition des indices de scénario, couples chauffe-eau et arrosage, mode immédiat, gestion des erreurs (429, 403, 422 `test_failed`, `stale`), absence du code d'alarme dans les journaux et le stockage.

#### Scenario: Suite unitaire
- **WHEN** on lance les tests unitaires
- **THEN** ils passent sans accès réseau à un serveur Essensys réel

### Requirement: Tests applicatifs
Les écrans Chauffage, Scénarios, Chauffe-eau, Arrosage, Alarme et Accueil SHALL être couverts par des tests UI sur émulateur Android et sur simulateur iOS, contre le faux backend embarqué, en cloud et en LAN.

#### Scenario: Parcours chauffage
- **WHEN** le test UI modifie un créneau puis enregistre
- **THEN** le faux backend reçoit le lot attendu et l'écran affiche la réussite

### Requirement: Garde no-armoire étendue
La garde no-armoire SHALL aussi bloquer en test toute requête vers un hôte réel sur `/api/portal/inject/batch`, `/api/portal/web/actions`, `/api/scenarios/*`, `/api/portal/scenarios/*` (lancement, restauration, enregistrement) et `/api/admin/heating/sync`.

#### Scenario: Envoi d'alarme vers la production
- **WHEN** un test tente d'appeler `/api/portal/web/actions` sur `mon.essensys.fr`
- **THEN** le test échoue avec « no-armoire : mutation réelle interdite »

### Requirement: Non-régression alignée
Les couples (k, v) et les règles de codage des nouvelles fonctions SHALL être verrouillés par des tests `NR-android-<n>` et `NR-ios-<n>` portant les mêmes numéros et les mêmes données sur les deux apps : codec du planning, indices des zones et modes immédiats, transposition des scénarios, chauffe-eau, arrosage. Ils SHALL référencer l'issue #35 et être consolidés par `nonreg_report.py`.

#### Scenario: Divergence entre les apps
- **WHEN** une modification change la valeur envoyée pour « Arrosage 30 minutes » dans une seule des deux apps
- **THEN** le test NR correspondant de cette app échoue

### Requirement: Checkup et livraison
`/checkup` SHALL être vert sur `essensys-android-phone-apps` et sur `essensys-ios-phone-apps`, avec les captures clair et sombre des nouveaux écrans postées sur l'issue #35. La version 2.1.0 SHALL être livrée aux testeurs : release GitHub signée (tag `android-v2.1.0`) pour Android, TestFlight (groupe famille) pour l'iPhone.

#### Scenario: Livraison
- **WHEN** les deux checkups sont verts
- **THEN** la release Android 2.1.0 et la build TestFlight 2.1.0 sont disponibles et annoncées sur #35

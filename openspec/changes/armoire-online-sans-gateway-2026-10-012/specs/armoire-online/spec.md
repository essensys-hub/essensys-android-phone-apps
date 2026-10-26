## Purpose

Permet d'utiliser l'app Android quand l'armoire est joignable, avec ou sans gateway.

## ADDED Requirements

### Requirement: Règle « armoire en ligne »
L'app SHALL considérer l'armoire en ligne si `session.gateway.online` **ou** `session.armoire.online` est vrai (`PortalSession.gateway.online` ou `PortalSession.armoire.online`). Un champ absent SHALL valoir faux. Le badge, le message « Armoire hors ligne » et l'état des commandes SHALL suivre cette règle ; les libellés ne changent pas. En mode LAN, les commandes SHALL rester actives.

#### Scenario: Sans gateway, armoire vue
- **WHEN** la session n'a pas de bloc `gateway` et `armoire.online = true`
- **THEN** le badge indique « Armoire en ligne » et les commandes sont actives

#### Scenario: Sans gateway, armoire ancienne
- **WHEN** la session n'a pas de bloc `gateway` et `armoire.online = false`
- **THEN** le badge indique « Armoire hors ligne » et les commandes sont désactivées

#### Scenario: Avec gateway hors ligne, armoire vue
- **WHEN** `gateway.online = false` et `armoire.online = true`
- **THEN** l'armoire est en ligne et les commandes sont actives

#### Scenario: Ancien backend
- **WHEN** la session n'a pas le champ `armoire.online`
- **THEN** la règle se réduit à `gateway.online`, comme avant

### Requirement: Tests avec et sans gateway
Les tests de essensys-android-phone-apps SHALL couvrir les cas A à G de la matrice (avec gateway et sans gateway), contre un backend simulé, garde no-armoire active ; le cas « sans gateway, armoire vue » porte la référence `#37` (`NR-android-19`).

#### Scenario: Suite de tests
- **WHEN** on lance les tests de essensys-android-phone-apps
- **THEN** ils couvrent les deux configurations et passent sans accès à une armoire réelle

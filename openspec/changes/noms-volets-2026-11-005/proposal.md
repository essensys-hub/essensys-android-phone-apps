> Ticket : [essensys-hub/essensys-feature-lifecycle#52](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/52). Feature ID : `noms-volets-2026-11-005`. Sub-issue : essensys-hub/essensys-android-phone-apps#47.
> Feature répartie sur plusieurs dépôts. Le change commun (proposal, design avec le « Contrat d'API », specs cloud, gateway et clients) vit dans `essensys-feature-lifecycle`, sous `openspec/changes/noms-volets-2026-11-005`. Ce change ne décrit que le code de **essensys-android-phone-apps** et en reprend la spec `shutter-names-clients`.

## Why

Les 16 volets et stores portent un nom d'origine figé dans l'app. L'administrateur de l'armoire doit pouvoir les renommer (« Volet 1 Salon » devient « Volet baie vitrée »), et le nom doit apparaître partout dans l'app, y compris dans la carte « Temps de course ».

## What Changes

- Lecture des noms : cloud `GET /api/portal/shutters/names`, réseau local `GET /api/shutters/names` (clé `shutters`). Repli sur les noms d'origine (`IndexTable.shutters`) si la lecture échoue, sans message bloquant.
- Nom affiché (nom personnalisé, sinon nom d'origine, texte brut) dans la liste des deux groupes, les confirmations d'ouverture et de fermeture, la carte « Temps de course » et ses messages (« temps de course réglé »). Les titres de cartes et les boutons génériques ne changent pas.
- Sous chaque volet, administrateur seulement (`SessionState.isAdmin`) : « Renommer » avec champ « Nom » (1 à 40 caractères, validé avant l'envoi), « Enregistrer » et « Nom d'origine ». Écriture `PUT /api/portal/admin/shutters/names/{id}` (cloud) ou `PUT /api/admin/shutters/names/{id}` (local), `{"name":"Volet baie vitrée"}` ou `{"name":null}`. Messages clairs pour 400 (`invalid_name`, `unknown_shutter`), 403 et 503.
- Renommer n'envoie aucun ordre à l'armoire (la route n'est pas une mutation d'armoire pour le garde-fou `no-armoire`). Le réglage du temps de course (indices 566 à 585) est inchangé.
- Version 2.7.0 posée par le tag `android-v2.7.0` (tâche 5.4, hors de ce change tant que le tag n'est pas fait ; aucun tag poussé).

## Capabilities

### New Capabilities

- `shutter-names-clients` : nom affiché partout et modification par l'administrateur (surface Android).

### Modified Capabilities

Aucune.

## Impact

- **Code** : `data/control` (`ShutterName`, `ShutterNames`, `ComfortRepository`), `data/http` (DTO), `ui/screens` (`ShutterNamesViewModel`, `ControlViewModel`, `ShuttersScreen`), `ui/AppRoot.kt`, tests unitaires et instrumentés, `FakeBackend`. Les noms des lumières (#41, `NR-android-25`) ne sont pas modifiés.
- **Compatibilité** : dépend des routes cloud et gateway déployées avant l'app ; sans elles (404, 503), les noms d'origine restent affichés et la modification échoue avec un message.

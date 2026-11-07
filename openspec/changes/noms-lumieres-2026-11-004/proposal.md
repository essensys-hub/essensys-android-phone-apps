> Ticket : [essensys-hub/essensys-feature-lifecycle#41](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/41). Feature ID : `noms-lumieres-2026-11-004`. Sub-issue : essensys-hub/essensys-android-phone-apps#45.
> Feature répartie sur plusieurs dépôts. Le change commun (proposal, design avec le « Contrat d'API », specs cloud, gateway et clients) vit dans `essensys-feature-lifecycle`, sous `openspec/changes/noms-lumieres-2026-11-004`. Ce change ne décrit que le code de **essensys-android-phone-apps** et en reprend la spec `light-names-clients`.

## Why

Les 33 lumières portent un nom d'origine figé dans l'app. L'administrateur de l'armoire doit pouvoir les renommer (« Dressing » devient « WC 3 »), et le nom doit apparaître partout dans l'app, y compris dans l'écran Temporisations.

## What Changes

- Lecture des noms : cloud `GET /api/portal/lights/names`, réseau local `GET /api/lights/names`. Repli sur les noms d'origine (`IndexTable.lights`) si la lecture échoue, sans message bloquant.
- Nom affiché (nom personnalisé, sinon nom d'origine, texte brut) dans les listes d'éclairage principal et indirect, la fiche de la lumière, l'écran Temporisations (qui n'utilise plus le libellé de `/lights/timers`) et les messages de confirmation.
- Fiche de la lumière, administrateur seulement (`SessionState.isAdmin`, notion de #45) : champ « Nom » (1 à 40 caractères, validé avant l'envoi), « Enregistrer » et « Nom d'origine ». Écriture `PUT /api/portal/admin/lights/names/{id}` (cloud) ou `PUT /api/admin/lights/names/{id}` (local), `{"name":"WC 3"}` ou `{"name":null}`. Messages clairs pour 400, 403 et 503.
- Renommer n'envoie aucun ordre à l'armoire (la route n'est pas une mutation d'armoire pour le garde-fou `no-armoire`).
- Version 2.6.0 posée par le tag `android-v2.6.0` (tâche 7.4, hors de ce change tant que le tag n'est pas fait).

## Capabilities

### New Capabilities

- `light-names-clients` : nom affiché partout et modification par l'administrateur (surface Android).

### Modified Capabilities

Aucune.

## Impact

- **Code** : `data/control` (`LightName`, `LightNames`, `ComfortRepository`), `data/http` (DTO), `ui/screens` (`LightNamesViewModel`, Éclairage, Temporisations), `ui/AppRoot.kt`, tests unitaires et instrumentés, `FakeBackend`.
- **Limites** : l'app n'a pas d'éditeur de scénarios qui nomme les lumières (il coche des bits de la table d'échange) : rien à renommer de ce côté. Le texte de « Dernière action » vient de l'armoire et reste tel quel.
- **Compatibilité** : dépend des routes cloud et gateway déployées avant l'app ; sans elles, les noms d'origine restent affichés et la modification échoue avec un message.

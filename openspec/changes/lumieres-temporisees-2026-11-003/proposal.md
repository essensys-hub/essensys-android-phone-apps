> Ticket : [essensys-hub/essensys-feature-lifecycle#45](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/45). Feature ID : `lumieres-temporisees-2026-11-003`. Sub-issue : essensys-hub/essensys-android-phone-apps#43.
> Feature répartie sur plusieurs dépôts. Le change commun (proposal, design avec le « Contrat d'API », specs cloud, gateway et clients) vit dans `essensys-memory`, sous `openspec/changes/lumieres-temporisees-2026-11-003`. Ce change ne décrit que le code de **essensys-android-phone-apps** et en reprend la spec `light-timers-clients`.

## Why

L'armoire éteint déjà automatiquement six lumières après un délai, mais seul son écran tactile le règle. L'app Android doit permettre ce réglage à l'administrateur de l'armoire.

## What Changes

- Écran « Temporisations » (tuile d'accueil, `Feature.LIGHT_TIMERS`) : les six lumières (Dressing 521, WC 1 555, WC 2 556, Service 557, Annexe 1 561, Annexe 2 562) avec leur délai ; raccourcis 15 min, 30 min, 1 h, Jamais et valeur libre de 1 à 255 minutes (0 et 300 refusés avant l'envoi).
- Le même réglage, replié, sous chacune de ces six lumières dans l'écran Éclairage.
- Lecture `GET …/lights/timers`, écriture `PUT …/lights/timers/{id}` `{"minutes":n}` (cloud `/api/portal/...` et `/api/portal/admin/...`, LAN `/api/admin/...`), mode test comme les autres écritures, relecture à 5, 10 et 20 s.
- **Rôle administrateur** : décodé à la connexion (cloud : `admin_local` ou `admin_global` ; LAN : `lan_admin`, ou IAM LAN désactivé d'après `GET /health`) et mémorisé dans `SessionState.isAdmin`. Menu et réglage masqués aux autres. Une session enregistrée avant cette version reste non administrateur jusqu'à la reconnexion.
- Version 2.5.0 posée par le tag `android-v2.5.0` (tâche 6.4, hors de ce change tant que le tag n'est pas fait).

## Capabilities

### New Capabilities

- `light-timers-clients` : menu central et réglage par lumière (surface Android).

### Modified Capabilities

Aucune.

## Impact

- **Code** : `data/auth` (`AdminRole`, `AuthRepository`, `SessionRenewer`), `data/session` (`isAdmin`, persistance), `data/control` (`LightTimer`, `ComfortRepository`), `data/http` (DTO, garde no-armoire), `ui/screens` (écran, ViewModel, Éclairage, accueil), tests unitaires et instrumentés, `FakeBackend`.
- **Table d'échange** : aucun indice legacy modifié ; écriture par la route dédiée (validation et rôle côté serveur).
- **Compatibilité** : dépend des routes cloud et gateway déployées avant l'app ; sans elles, l'écran affiche « inconnue » et l'envoi échoue avec un message.

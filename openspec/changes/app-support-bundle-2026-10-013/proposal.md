> Ticket : [essensys-hub/essensys-feature-lifecycle#38](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/38). Feature ID : `app-support-bundle-2026-10-013`.
> Feature répartie sur 2 dépôts, un change du même nom dans chacun : apps Android et iPhone. Ce change ne décrit que le code de **essensys-android-phone-apps**.

## Why

Le 2026-10-30, un utilisateur pilote a écrit « rien ne fonctionne » avec une seule capture d'écran. Il a fallu deviner la version installée, le réseau (Wi-Fi ou 4G) et l'état du mode test. L'appli ne sait produire aucun diagnostic et ne tient aucun journal : chaque demande de support coûte plusieurs allers-retours.

## What Changes

- Bouton **Support** dans Réglages et sur l'écran de connexion (l'utilisateur qui n'arrive pas à se connecter doit pouvoir l'utiliser).
- Série de **tests techniques en lecture seule** lancés depuis le téléphone : appareil et appli, réseau (DNS, temps de réponse HTTPS vers mon.essensys.fr, certificat), portail cloud, gateway en mode local, réglages de l'appli. Aucune commande n'est envoyée à l'armoire.
- **Journal interne** des requêtes et événements de l'appli, sans contenu sensible.
- **Bundle zip** (résumé, résultats détaillés, journal) et ouverture de l'appli email vers l'adresse support (`support_essensys@rhinosys.fr` pour l'instant), avec le zip en pièce jointe.
- Version 2.3.0, release `android-v2.3.0`.

## Capabilities

### New Capabilities

- `support-bundle` : tests de diagnostic, journal interne, contenu et confidentialité du bundle, envoi par email.
- `support-bundle-quality` : tests sans armoire, test NR de non-fuite des secrets, checkup.

### Modified Capabilities

Aucune (les specs existantes de ce dépôt ne sont pas archivées sous `openspec/specs/`).

## Impact

- **Code** : `data/http` (journal des requêtes), nouveau paquet `data/support` (diagnostics, bundle), `ui/screens` (Réglages, écran de connexion, écran Support), `AndroidManifest.xml` (FileProvider).
- **Serveur** : lecture seule de `GET /api/portal/health` (cloud) et `GET /health` (gateway), déjà en place.
- **Table d'échange** : aucun indice lu ni écrit par les tests de diagnostic.
- **Dépendances** : aucune nouvelle (`java.util.zip`, `ConnectivityManager`).

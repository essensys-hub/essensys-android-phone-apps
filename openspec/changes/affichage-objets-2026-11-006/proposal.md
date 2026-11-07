> Ticket : [essensys-hub/essensys-feature-lifecycle#55](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/55). Feature ID : `affichage-objets-2026-11-006`. Sub-issue : essensys-hub/essensys-android-phone-apps#49.
> Feature répartie sur plusieurs dépôts. Le change commun (proposal, design avec le « Contrat d'API », specs cloud, gateway et clients) vit dans `essensys-feature-lifecycle`, sous `openspec/changes/affichage-objets-2026-11-006`. Ce change ne décrit que le code de **essensys-android-phone-apps** et en reprend la spec `object-layout-clients`.

## Why

Les 33 lumières et les 16 volets et stores sont tous affichés, dans l'ordre d'origine de l'app. L'administrateur de l'armoire doit pouvoir masquer ceux qui ne servent pas et choisir l'ordre d'affichage, pour tous les utilisateurs.

## What Changes

- Lecture de la disposition : cloud `GET /api/portal/{lights|shutters}/layout`, réseau local `GET /api/{lights|shutters}/layout`. Règle commune `effectiveLayout` (`order` d'abord, puis les identifiants connus absents dans l'ordre du catalogue, identifiants inconnus ignorés). Repli sur le catalogue complet dans l'ordre d'origine si la lecture échoue ou répond 404, sans message bloquant ; la dernière disposition lue est conservée en cas de coupure.
- Un objet désactivé est masqué dans les listes (`LightingScreen`, `ShuttersScreen`), dans « Tout allumer / éteindre » et « Tout ouvrir / fermer » (la liste du groupe alimente les boutons et les lignes), dans Temporisations (`LightTimerScreens`) et dans la carte « Temps de course » (ainsi que la lecture des temps). Un groupe sans objet actif affiche « Aucune lumière activée » / « Aucun volet activé » sans bouton de groupe. L'ordre s'applique à l'intérieur de chaque groupe ; les écrans sans groupe suivent l'ordre global filtré. Scénarios et éditeur de scénarios : catalogue complet, inchangé.
- Écran de gestion, administrateur seulement (`SessionState.isAdmin`), depuis Éclairage et Volets : tous les objets par groupe, interrupteur Activé/Désactivé, Monter/Descendre (échange avec le voisin du même groupe), « Ordre d'origine » et « Enregistrer » qui envoie **une seule écriture** `PUT /api/portal/admin/{lights|shutters}/layout` (cloud) ou `PUT /api/admin/{lights|shutters}/layout` (local), `{"order":[…],"disabled":[…]}` ou `{"reset":true}`. Messages clairs pour 400, 403 et 503 ; l'écran reste ouvert avec les modifications.
- Aucun ordre à l'armoire : la disposition n'est ni lue ni écrite dans la table d'échange. Les noms (#41, #52, `NR-android-25` et `NR-android-26`), les temporisations et les indices (`NR-android-5` et `NR-android-6`) sont inchangés.
- Version 2.8.0 préparée (notes dans le README, `release.target_tag` du manifest) ; le tag `android-v2.8.0` reste à poser (tâche 5.4), aucun tag poussé.

## Capabilities

### New Capabilities

- `object-layout-clients` : disposition appliquée partout et gestion par l'administrateur (surface Android).

### Modified Capabilities

Aucune.

## Impact

- **Code** : `data/control` (`ObjectLayout`, `ComfortRepository`), `data/http` (DTO), `ui/screens` (`ObjectLayoutViewModel`, `LayoutManager`, `LightingScreen`, `ShuttersScreen`, `LightTimerScreens`), `ui/AppRoot.kt`, tests unitaires et instrumentés, `FakeBackend`. L'ordre d'origine de `IndexTable` ne change pas (clé de repli et de test).
- **Compatibilité** : dépend des routes cloud et gateway déployées avant l'app ; sans elles (404, 503), le catalogue complet reste affiché et l'enregistrement échoue avec un message.
- **Écart au design** : l'écran de gestion est une carte dépliable dans l'écran (et non une feuille modale) ; mêmes contrôles, mêmes droits.

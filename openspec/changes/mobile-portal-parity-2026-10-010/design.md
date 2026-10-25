## Context

Motivation : voir `proposal.md`. Besoins : les 6 specs du change.

**État de départ** (2026-10-25) : app v2.0.0 (`android-portal-refresh-2026-10-002`) avec `IndexTable` (éclairage, volets), `ControlRepository` cloud et LAN (inject unitaire), `PortalRepository` (session, dernière action), `NoArmoireInterceptor`, faux backend de test et 5 onglets prévus pour 4 destinations (Accueil, Éclairage, Volets, Réglages).

**Références lues dans le code le 2026-10-25** (à ne pas réinventer) :
- portail : `essensys-user-portal-frontend/src/pages/*`, `src/heating/*` (`scheduleCodec.ts`, `injectLimits.ts`, `scheduleSync.ts`), `components/Scenarios/*` ;
- cloud : `essensys-user-portal-backend/internal/handlers/portal.go` (exchange, inject, inject/batch, web/actions), `handlers/scenarios.go`, `domain/scenario/*` ;
- LAN : `essensys-server-backend/internal/api/handlers.go` (inject en tableau, exchange, heating/sync), `handlers_scenarios.go`, `armoire/decode.go` ;
- firmware : `client-essensys-legacy/H/TableEchange.h` (`Chauf_*_Mode` 349–352, `Cumulus_Mode` 353, `Arrose_Mode` 363, `Alarme_CodeSaisiLSB/MSB`).

**Contraintes des backends** :
- 30 paramètres au plus par action firmware ; `inject/batch` cloud accepte 96 paramètres et découpe lui-même, le portail découpe déjà côté client ;
- une seule limite cloud de 30 requêtes par minute pour inject, inject/batch, lancement et restauration de scénario ;
- la synchronisation réelle du chauffage n'existe qu'en LAN ; en cloud, `exchange` sert un cache poussé par la gateway (`stale`, `source`) ;
- `/api/web/actions` (alarme) n'est enregistrée en LAN que si l'IAM LAN est désactivé : la session LAN de l'app n'y a donc pas accès.

## Goals / Non-Goals

**Goals :**
- Couvrir les nouvelles fonctions avec la même architecture que la v2.0.0, sans nouvelle dépendance.
- Garder Android et iPhone strictement identiques sur les couples envoyés (tests NR aux mêmes numéros).

**Non-Goals :**
- Toute modification des backends, du portail ou des indices de la table d'échange : les défauts constatés partent en tickets séparés.
- Lire l'état réel de l'armoire au-delà de ce que `exchange` expose (pas de snapshot cloud).

## Decisions

### D1 — Android d'abord, iPhone en miroir
Comme en v2.0.0, chaque groupe de tâches est livré d'abord sur Android, puis porté sur l'iPhone avec les mêmes fixtures et les mêmes tests NR. Les deux PR d'un groupe portent `Feature: mobile-portal-parity-2026-10-010`.

*Alternative écartée* : une couche partagée Kotlin Multiplatform. Elle imposerait une refonte de l'app iPhone et une nouvelle chaîne de build pour 6 écrans.

### D2 — `IndexTable` étendue et codecs purs
- `IndexTable` reçoit les zones de chauffage (début de planning, indice de mode, anticipé autorisé), les modes immédiats, les modes du chauffe-eau et de l'arrosage, et la formule de bloc des scénarios.
- Le codage du planning est une fonction pure (`ScheduleCodec` : décodage, encodage, diff contre la référence, copie d'un jour), portée ligne à ligne de `scheduleCodec.ts`.
- Le découpage en lots est une fonction pure (`chunk(params, 30)`).
- Tout cela est testable sans réseau et verrouillé par les NR.

### D3 — Indices du firmware pour le chauffe-eau et l'arrosage
Le portail envoie `k=400..402` et `k=500..504` avec `v="1"`, absents de `TableEchange.h` et des décodeurs LAN. On suit le firmware (353 avec 0/1/2, 363 avec 0/15/30/60/255) : c'est la source de vérité de l'armoire, et le décodeur LAN `armoire/decode.go` la confirme. Un ticket séparé corrige le portail.

*Alternative écartée* : copier le portail pour la parité. L'app enverrait des commandes que l'armoire ignore.

### D4 — Repositories par fonction, deux implémentations par mode
`HeatingRepository`, `ScenarioRepository` et `ComfortRepository` (chauffe-eau, arrosage, alarme) suivent le modèle de `ControlRepository` : une implémentation cloud (`/api/portal/*`, Bearer) et une LAN (`/api/admin/*` ou `/api/scenarios/*`, cookie), choisies à la connexion. En LAN, l'alarme renvoie une erreur typée « non disponible » sans appel réseau.

### D5 — Envoi du planning : séquentiel, interruptible, sans relecture
Les lots partent l'un après l'autre (cloud `inject/batch`, LAN `inject` en tableau), comme le portail. La progression est un `StateFlow` d'événements (`Start`, `Sending(i, n, kMin, kMax)`, `Success`, `Error`, `Done`). En cas d'échec, l'envoi s'arrête et seuls les octets non envoyés restent « modifiés ». Après succès, la référence devient la grille, sans relecture (le cache cloud est mis à jour par `inject/batch`).

### D6 — Scénarios : transposition des masques
Les métadonnées `meta/bitmasks` décrivent les indices du scénario 1. L'app les transpose dans le bloc du scénario édité (spec `scenarios`) et refuse localement tout indice hors bloc, avant l'appel. Le portail ayant le même défaut, un ticket est ouvert ; si le backend corrige ses métadonnées, la transposition devient l'identité et le test NR le montrera.

### D7 — Alarme : code éphémère
- Le code vit seulement dans l'état de l'écran (pas de `rememberSaveable`, pas de `SavedStateHandle`) et il est effacé après envoi et à `ON_STOP`.
- Le champ utilise `KeyboardType.NumberPassword` et désactive les suggestions.
- Le journal HTTP de debug masque le corps des requêtes `/web/actions`.
- La réponse dry-run (`validated_params`, qui contient le code) n'est pas affichée.

### D8 — Navigation à 5 onglets et accueil à tuiles
Onglets : Accueil, Éclairage, Volets, Chauffage, Réglages (Material 3 `NavigationBar`, 5 entrées au maximum). Scénarios, Chauffe-eau, Arrosage et Alarme sont des écrans poussés depuis l'accueil. Les scénarios rapides de l'accueil réutilisent le `ScenarioRepository`. L'iPhone applique la même structure avec `TabView`.

*Alternative écartée* : un onglet « Plus ». Il cache les fonctions fréquentes et diffère du tableau de bord du portail.

### D9 — Tests et NR
- **Unitaires** : `MockWebServer`, fixtures JSON reprises des réponses Go réelles (exchange avec `stale`, scenarios, bitmasks, test_ok, 409 busy).
- **Instrumentés** : faux backend étendu aux nouvelles routes, en cloud et en LAN.
- **NR** : `NR-android-10` et suivants, mêmes numéros et mêmes données que `NR-ios-10` et suivants : planning (codage d'un octet, indices des 4 zones, découpage 40 → 30 + 10), modes immédiats, transposition de scénario, chauffe-eau, arrosage, code d'alarme absent des journaux.
- **Garde no-armoire** : regex étendues aux nouvelles routes de mutation.

### D10 — Version et livraison
`versionName` 2.1.0, release signée par tag `android-v2.1.0` (workflow `release.yml` existant). Les groupes peuvent être livrés en PR séparées ; la release attend le groupe Qualité.

## Risks / Trade-offs

- [Indices 353 et 363 jamais testés depuis le portail] → la valeur est confirmée par `TableEchange.h` et le décodeur LAN ; la première validation réelle sera faite par un humain sur le banc BP, jamais par les tests automatiques.
- [Encodage du code d'alarme : le firmware attend un chiffre par quartet, les backends envoient « 12 » en décimal] → l'app n'encode rien (le backend le fait). L'écran Alarme reste masqué dans la release tant qu'un humain n'a pas validé la mise en service sur le banc ou que le ticket backend n'est pas corrigé.
- [Code d'alarme stocké en clair côté cloud et sans limite de débit] → hors de ce change ; ticket backend. L'app ne l'aggrave pas (pas de stockage, pas de journal).
- [Limite de 30 requêtes par minute partagée] → un planning complet tient en 3 lots ; un message 429 clair et la reprise des lots restants.
- [Cache cloud ancien pour le planning] → avertissement `stale` visible avant édition.
- [Écran Chauffage dense sur petit écran] → grille défilable par jour, vérifiée à 360 dp et sur iPhone SE.

## Migration Plan

1. Branche `feat/mobile-portal-parity-2026-10-010` dans chaque dépôt, une PR par groupe de tâches ou par lot de groupes, avec `Feature: mobile-portal-parity-2026-10-010` et `Refs`/`Closes` vers les sub-issues.
2. Livraison 2.1.0 aux testeurs après les deux `/checkup` verts.
3. Rollback : la 2.0.0 reste disponible (release GitHub Android, build TestFlight précédente).

## Open Questions

- Libellés exacts des modes du chauffe-eau dans l'app (« Autonome » ou « Marche forcée ») : à valider avec un testeur, sans effet sur les couples envoyés.

## Context

Motivation : voir `proposal.md`. État des lieux (2026-10-30) : l'appli ne tient aucun journal ; la version n'est visible que dans Réglages (corrigé à part par #27) ; le client HTTP est un `OkHttpClient` unique créé par `HttpClientFactory`, avec `SessionInterceptor` (Bearer ou cookie) et `NoArmoireInterceptor` en test ; le portail cloud expose `GET /api/portal/health` sans authentification, la gateway `GET /health`.

**Décisions communes aux 2 dépôts** (le détail propre à chaque dépôt suit) :
- **C1 — Même diagnostic** : mêmes tests, mêmes libellés, mêmes statuts (`ok`, `warning`, `error`, `n/a`) et même schéma de `diagnostics.json` sur Android et iPhone. Le champ `platform` les distingue.
- **C2 — Temps de réponse plutôt que ping** : l'iPhone n'autorise pas l'ICMP. Les deux apps mesurent 5 requêtes `GET /api/portal/health` (HTTPS), ce qui teste aussi DNS, TLS et le reverse proxy.
- **C3 — Lecture seule** : les diagnostics n'appellent que `health`, `session`, `gateway/status`, `history/latest` (cloud) et `health` (LAN). Jamais d'injection.
- **C4 — Liste blanche** : le bundle est construit à partir de champs explicitement choisis, jamais en sérialisant un objet de session ou une requête entière. Le journal n'enregistre jamais de corps ni d'en-tête.
- **C5 — Envoi par l'appli email de l'utilisateur** : pas de serveur de collecte. L'utilisateur voit et peut modifier le message. L'adresse support est une constante de build, changeable sans toucher au code des écrans.

## Goals / Non-Goals

**Goals :** voir les specs de ce change.

**Non-Goals :** envoi automatique ou en arrière-plan ; collecte côté serveur ; diagnostic de l'armoire elle-même (bus I2C, BA) ; journal des crashs natifs (à traiter avec un outil dédié si besoin).

## Decisions

### D1 — Journal : `AppLog` en anneau, persisté
`data/support/AppLog` : tampon de 500 entrées en mémoire, écrit en JSON lignes dans `filesDir/support/journal.jsonl` (réécrit à la fermeture et toutes les 50 entrées). Alimenté par un `LoggingInterceptor` ajouté dans `HttpClientFactory` **avant** `SessionInterceptor` : il ne voit donc pas les en-têtes d'authentification. Il note méthode, `url.encodedPath`, code, durée, classe d'exception. Les ViewModels notent les événements (connexion, changement d'état en ligne, mode test).
*Alternative écartée* : `Logcat` / `Timber`, illisible sans ADB et sans contrôle de ce qui est écrit.

### D2 — Diagnostics : `SupportDiagnostics`
Une coroutine par test, chacune sous `withTimeout` (8 s ; 25 s au total). DNS par `InetAddress.getAllByName`, réseau par `ConnectivityManager.getNetworkCapabilities` (Wi-Fi, cellulaire, VPN), horloge par l'en-tête `Date` de `health`. Les requêtes passent par un client dérivé (`newBuilder()`) avec `SessionInterceptor` pour la session, mais sans `LoggingInterceptor`, pour ne pas polluer le journal. L'état « en ligne » est lu dans le `SessionViewModel` existant avec la règle appliquée (gateway ou armoire, voir armoire-online-sans-gateway-2026-10-012).

### D3 — Bundle : `SupportBundle`
`java.util.zip.ZipOutputStream` dans `cacheDir/support/`, nom selon la spec. `resume.txt` est le même texte que le résumé affiché. Les anciens bundles sont supprimés à chaque nouvelle génération.

### D4 — Email : `ACTION_SEND` avec sélecteur `mailto:`
`Intent(ACTION_SEND)` de type `application/zip` avec `EXTRA_EMAIL`, `EXTRA_SUBJECT`, `EXTRA_TEXT`, `EXTRA_STREAM` (URI `FileProvider`, `FLAG_GRANT_READ_URI_PERMISSION`) et `selector = Intent(ACTION_SENDTO, "mailto:")` pour ne proposer que les applis email. Si `resolveActivity` ne trouve rien, repli sur `Intent.createChooser` sans sélecteur. Adresse dans `BuildConfig.SUPPORT_EMAIL` (`support_essensys@rhinosys.fr`).

### D5 — Écran Support
Écran poussé (`Feature.SUPPORT`) depuis Réglages et depuis l'écran de connexion : liste des tests avec leur statut au fil de l'eau, puis bouton « Envoyer au support » et « Relancer ». Accessible en release.

### D6 — Tests
Unitaires (`AppLog`, assemblage du bundle, statuts), MockWebServer (health OK, délai, 401, LAN), `NR-android-19` (non-fuite des secrets), instrumentés (`SupportFlowTest` : écran, résumé, intent email vérifié par Espresso-Intents, aucun appel `inject`).

## Risks / Trade-offs

- [Fuite d'un secret dans le journal ou le bundle] → C4 (liste blanche), `LoggingInterceptor` placé avant l'authentification, `NR-android-19`.
- [Certaines applis email ignorent `EXTRA_STREAM` avec le sélecteur `mailto:`] → validation manuelle sur Gmail et Outlook ; repli sur la feuille de partage documenté.
- [Les tests durent trop en 4G faible] → délais par test et total plafonné à 25 s ; résultat partiel envoyé quand même.
- [Journal trop volumineux] → 500 entrées, chemins sans paramètres : quelques dizaines de Ko.
- [Données personnelles (email du compte, numéro de série de l'armoire)] → nécessaires au support et envoyées par l'utilisateur lui-même depuis sa propre boîte ; mentionnées dans le résumé affiché avant envoi.

## Migration Plan

1. Une PR sur essensys-android-phone-apps avec `Feature: app-support-bundle-2026-10-013` et `Refs`/`Closes` vers les sub-issues.
2. Vérification réelle par un humain (Wi-Fi puis 4G), puis release `android-v2.3.0`.
3. Rollback : masquer le bouton Support ; le journal reste local et sans effet sur le reste de l'appli.

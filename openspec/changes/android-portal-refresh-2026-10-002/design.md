## Context

Pour la motivation, voir `proposal.md`. Les besoins sont décrits dans les 5 specs.

**État de départ** (analyse du 2026-10-09) :
- **Code** : un singleton `EssensysAPI` (OkHttp, Basic Auth, `/api/admin/inject` seulement, bascule en démo en cas d'erreur), 7 écrans Compose dans `ui/`, aucun test.
- **Stack** : AGP 8.2.2, Kotlin 2.0.0, Gradle 8.6, Compose BOM 2023.08, SDK 34, Java 8.
- **Références**, à ne pas réinventer :
  - cloud : `essensys-user-portal-frontend`, avec les routes de `essensys-user-portal-backend/internal/portal/routes.go` et `internal/identity` ;
  - LAN : `essensys-server-backend/internal/api/router.go`, avec `/api/auth/login` (LanIAM) et `/api/admin/inject` protégé.
- **TLS en LAN** : chaque gateway génère **sa propre CA** (`essensys-ansible/roles/raspberry_traefik`, tag `localtls`). Il n'existe donc pas de CA commune à embarquer dans l'app.

## Goals / Non-Goals

**Goals :**
- Une couche data unique et testable, quel que soit le mode.
- La parité V1 avec le portail : connexion, liaison, état, éclairage, volets, thème.
- Des tests sans réseau réel.
- Un APK signé et traçable.

**Non-Goals :**
- Les écrans V2 (chauffage, scénarios, chauffe-eau, arrosage, alarme), OAuth Google et Apple, les notifications push, les plugins et UniFi.
- La publication sur le Play Store.
- Toute modification des backends ou des indices de la table d'échange.

## Decisions

### D1 — Architecture : une seule activité, Compose, une couche `data` par interfaces
- Les paquets sont `data/` (client HTTP, `AuthRepository`, `ControlRepository`, `SessionRepository`, `IndexTable`), `ui/` (thème, écrans, ViewModels) et `di/`.
- L'injection de dépendances est manuelle, via un `AppContainer`.
- Les écrans sont pilotés par des `ViewModel` + `StateFlow`.

*Alternative écartée* : Hilt. C'est un surcoût de build et de complexité pour 5 écrans. On pourra le revoir en V2 si l'app grossit.

### D2 — HTTP : OkHttp + kotlinx.serialization, deux backends derrière une même interface
- `ControlRepository` a deux implémentations : `CloudControlRepository` (`/api/portal/*`, Bearer) et `LanControlRepository` (`/api/admin/*`, cookie).
- Le mode actif choisit l'implémentation au moment de la connexion.
- Une chaîne d'intercepteurs gère l'auth, le dry-run (`X-Essensys-Test-Mode`), la conversion 401/409 en événements de session, et la garde no-armoire (en debug et en test seulement).
- Retrofit, aujourd'hui inutilisé, est retiré.

*Alternative écartée* : Ktor client. OkHttp est déjà en place, et `MockWebServer` est le standard de test.

### D3 — Stockage sécurisé : DataStore + chiffrement AES-GCM par une clé Android Keystore
- Le JWT, le cookie LAN et le mode sont stockés dans un DataStore dont les valeurs sensibles sont chiffrées par une clé Keystore non exportable.
- Au premier lancement, `EssensysPrefs` (la v1.0.0, en clair) est purgé.

*Alternative écartée* : `androidx.security:security-crypto` (EncryptedSharedPreferences), déprécié par Google en 2025.

### D4 — TLS en LAN : épinglage TOFU de la CA de la gateway
- Le cloud utilise le magasin système (certificat public).
- En LAN, à la première connexion à un hôte, l'app récupère la chaîne présentée et affiche l'empreinte SHA-256 de la CA.
- L'utilisateur la compare avec celle affichée par l'installateur ou par la page d'installation de la gateway, puis confirme.
- La CA est alors épinglée (`TrustManager` dédié, limité à cet hôte), et la vérification du nom d'hôte reste active.
- Si la CA change, l'app bloque la connexion avec une alerte explicite.
- Aucun `usesCleartextTraffic` et aucun « trust all ».

*Alternatives écartées* :
- les CA utilisateur via network-security-config : installation manuelle complexe pour le client, et confiance accordée à tout le magasin utilisateur ;
- une CA Essensys commune : elle n'existe pas aujourd'hui.

### D5 — Indices : table unique `IndexTable` portée depuis le code du portail
- Les couples (k, masque) d'éclairage et de volets sont copiés du code du portail, qui fait foi : 611–616 / 605–610 et 617–619 / 620–622.
- Le wiki `table-d-echange.md` décrit 617/618 pour le PDV. Le code du portail et la console cuisine (feature 2026-06-031, 619 / 622) sont plus récents et vérifiés.
- **On suit le portail.** L'écart du wiki est signalé à `essensys-memory` par une tâche de doc.
- Chaque couple est verrouillé par un test `NR-android-*`.

### D5 bis — Contrats réels (lus dans le code Go le 2026-10-09)
- Cloud : login 200 `{token, user, password_change_required}` (jamais 409 au login) ; 409 `password_change_required` sur `/api/portal/*` ; `POST /api/auth/password/change` garde le même JWT ; 403 `account_forbidden` ; erreurs majoritairement `text/plain` ; 429 sans `Retry-After` ; inject mis en file même gateway hors ligne (pas de 503).
- LAN : login `{email, password}` → cookie `essensys_lan_session` (Secure, HttpOnly, 168 h) + `{user}` ; pas de 409, pas de session/historique ; `PUT /api/user/me/password` détruit toutes les sessions ; `/api/admin/inject` accepte toute session valide ; `GET /api/user/me` pour vérifier la session.
- Conséquence : un `ErrorBody` tolérant (JSON `error`/`message` sinon texte), et une UI de session différente selon le mode.

### D6 — Rafraîchissement lié au cycle de vie
- Le polling de `session` (60 s) et de `history/latest` (2 s) tourne dans `repeatOnLifecycle(STARTED)`. Il s'arrête donc dès que l'app est en arrière-plan.
- L'anti-rebond est porté par le ViewModel : une commande en vol par cible.

### D7 — Thème : `EssensysTheme` Material 3 alimenté par les tokens du portail
- Les tokens du portail sont mappés sur les rôles M3 : primary, surface et surfaceContainer pour les cartes, outline pour les bordures, error, etc.
- Ils sont complétés par des `CompositionLocal` pour `success`, `warning` et l'en-tête de carte.
- Les formes sont `small = 8 dp` et `medium = 12 dp`.
- Pas de couleur dynamique (Material You), pour garder la charte.

### D8 — Tests
- **Unitaires** : JVM avec `MockWebServer` (repositories, intercepteurs, 401/409, `IndexTable`, anti-rebond).
- **Instrumentés** : `createAndroidComposeRule` avec un `AppContainer` de test qui pointe vers `MockWebServer` sur l'émulateur.
- **Fixtures JSON** : portées depuis `mockFetch.ts` et `mockScenarios.ts` du portail.
- **Garde no-armoire** : `NoArmoireInterceptor`, avec les regex de `noArmoire.ts`. Toute mutation vers un hôte autre que `127.0.0.1` ou `localhost` lève une exception.
- **Rapports** : JUnit XML de Gradle, consommé par `nonreg_report.py`.

### D9 — Signature et release
- La `signingConfig` release lit `ESSENSYS_KEYSTORE_PATH`, `ESSENSYS_KEYSTORE_PASSWORD`, `ESSENSYS_KEY_ALIAS` et `ESSENSYS_KEY_PASSWORD` depuis l'environnement. Si une variable manque, le build échoue clairement.
- Le keystore et ses mots de passe sont stockés avec SOPS dans `essensys-ansible/secrets/` (`android-release.keystore.sops`).
- Un workflow `release.yml` se déclenche sur tag `android-v*` : il déchiffre via un secret CI, assemble, signe, calcule le SHA-256 et crée la release GitHub.
- Version : `versionName` 2.0.0, `versionCode` incrémenté.

## Risks / Trade-offs

- [TOFU : l'utilisateur confirme une empreinte sans la vérifier] → texte explicite, empreinte affichée aussi sur la gateway. Le cloud reste le mode par défaut, recommandé au client.
- [Le JWT 24 h sans refresh oblige à se reconnecter chaque jour] → on accepte en V1 (parité avec le portail). Une demande d'endpoint de refresh est à ouvrir côté backend en V2.
- [Login cloud direct alors que le portail web passe par le support-site] → `POST /api/auth/login` est l'API publique, sans Turnstile au login. À surveiller si un captcha est ajouté.
- [Rate-limit sur inject] → anti-rebond, et message 429 lisible.
- [La suppression des écrans factices ressemble à une perte de fonctions] → l'accueil affiche « Bientôt disponible » avec la liste V2, pour être honnête avec le client.
- [Pas d'AVD sur le poste du mainteneur] → la création d'un AVD (system image arm64 déjà présente) est la première tâche de test.

## Migration Plan

1. Branche `feat/android-portal-refresh-2026-10-002`, PRs contenant `Feature: android-portal-refresh-2026-10-002` et `Refs` ou `Closes` vers les sub-issues.
2. Livraison interne : APK debug et `/checkup` vert à chaque PR.
3. Release 2.0.0 signée, transmise au client avec la procédure d'installation (suppression de la v1.0.0 conseillée : identifiants purgés).
4. Rollback : la v1.0.0 reste téléchargeable dans les releases GitHub (l'APK est retiré de l'arbre git, pas de l'historique).

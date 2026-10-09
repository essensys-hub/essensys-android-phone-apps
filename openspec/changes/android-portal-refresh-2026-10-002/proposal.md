> Ticket : [essensys-hub/essensys-feature-lifecycle#11](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/11). Feature ID : `android-portal-refresh-2026-10-002`. C'est le pilote du lifecycle GitHub Project (`github-project-lifecycle-2026-10-001`).

## Why

Un client clé veut tester Essensys sur Android, mais l'app actuelle (v1.0.0, janvier 2026) a décroché du portail sur trois plans.

- **Fonctionnel** :
  - l'app appelle `/api/admin/inject` en Basic Auth ;
  - elle ne lit aucun état ;
  - plusieurs écrans sont factices (chauffage, arrosage, scènes) ;
  - au moindre échec réseau, elle bascule silencieusement en « mode démo ».
- **Sécurité** :
  - le trafic HTTP passe en clair ;
  - le mot de passe est stocké en clair dans `SharedPreferences` ;
  - un APK et des dumps `.hprof` sont versionnés dans le dépôt.
- **Visuel** : l'app n'a ni la charte du portail ni de mode sombre.

On ne peut pas confier cette app à un client.

## What Changes

- **BREAKING** — Nouvelle authentification alignée sur le portail.
  - Cloud : `POST /api/auth/login`, puis JWT Bearer sur `/api/portal/*`, avec gestion de l'expiration à 24 h et du `409 password_change_required`.
  - LAN : session `essensys_lan_session` sur `mon.essensys.local`.
  - Fin du Basic Auth et du stockage du mot de passe en clair.
- Deux modes de connexion, **cloud** (`mon.essensys.fr`) et **LAN** (`mon.essensys.local`), choisis par l'utilisateur.
  - Gate de liaison armoire (`link-request`).
  - État de la gateway et dernière action.
  - HTTPS uniquement.
- Écrans V1 **Éclairage** et **Volets**, alignés sur le portail.
  - Mêmes indices et masques que le portail.
  - Lecture d'état quand elle existe, retour visuel du résultat, anti-rebond.
  - **Suppression du mode démo silencieux.**
- Charte du portail : tokens de couleur clair et sombre, rayons, composants carte et bouton.
- Mise à jour de la stack : AGP, Kotlin, Compose BOM récents, SDK cible exigé par le Play Store, Java 17.
- Qualité :
  - tests unitaires JUnit ;
  - tests instrumentés sur émulateur ;
  - tests de non-régression `NR-android-*` ;
  - garde no-armoire (aucune requête de mutation réelle en test) ;
  - `/checkup` vert.
- Livraison : APK release **signé** (keystore hors dépôt, SOPS) publié en **release GitHub** pour une installation manuelle par le client. Nettoyage du dépôt (APK, `.hprof`).
- Hors V1, repoussés en V2 (tickets séparés) : chauffage, scénarios, chauffe-eau, arrosage, alarme, plugins, UniFi, OAuth Google et Apple. Les écrans factices actuels sont **retirés** de la V1 plutôt que laissés trompeurs.

## Capabilities

### New Capabilities

- `mobile-auth` : connexion cloud (JWT) et LAN (session cookie), stockage sécurisé, expiration, changement de mot de passe obligatoire, déconnexion.
- `connection-modes` : choix cloud / LAN, gate de liaison armoire, état de la gateway, dernière action, HTTPS, absence de mode démo implicite.
- `domotic-controls` : éclairage et volets V1 (indices, masques, lecture d'état, résultat, anti-rebond, mode test dry-run).
- `portal-theme` : charte du portail en clair et en sombre.
- `android-quality` : stack, tests unitaires, instrumentés et NR, garde no-armoire, `/checkup`, livraison APK signé.

### Modified Capabilities

<!-- Aucune spec existante dans ce dépôt (OpenSpec initialisé par ce change). -->

## Impact

- `app/src/main/java/com/essensys/android/**` : réécriture des couches data (API, auth, stockage) et UI (navigation, thème, écrans).
- `app/build.gradle.kts`, `build.gradle.kts`, `gradle/` : versions, signing config lue depuis l'environnement, dépendances (DataStore, security-crypto ou Keystore, MockWebServer).
- `AndroidManifest.xml` : suppression de `usesCleartextTraffic`, ajout d'une `network-security-config`.
- Nouveaux `app/src/test`, `app/src/androidTest`, workflow CI (`nonreg.yml` réutilisable, build).
- `essensys-ansible/secrets/` : keystore et mots de passe de signature (SOPS).
- Dépôt : suppression de l'APK versionné, des `.hprof` et de `app/release/` ; README, captures, doc utilisateur.
- Backends : aucun changement prévu. On consomme les API existantes de `essensys-user-portal-backend` et de `essensys-server-backend`.

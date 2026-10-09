## Purpose

Garantit que l'app Android est construite sur une stack à jour, testée automatiquement sans jamais piloter d'armoire réelle, vérifiable par `/checkup`, et livrée au client sous forme d'APK signé traçable.

## ADDED Requirements

### Requirement: Stack à jour
L'app SHALL cibler le niveau d'API exigé par le Play Store à la date de livraison, compiler avec Java 17, et utiliser des versions stables récentes d'AGP, Kotlin et Compose BOM. `minSdk` SHALL rester 26. Les dépendances inutilisées (Retrofit) SHALL être retirées.

#### Scenario: Build propre
- **WHEN** on exécute `./gradlew assembleDebug lintDebug`
- **THEN** le build réussit sans erreur de lint bloquante

### Requirement: Tests unitaires et instrumentés
Le dépôt SHALL contenir des tests unitaires JUnit (couche data : auth, gestion 401/409, table d'indices, anti-rebond) exécutés par `./gradlew testDebugUnitTest`, et des tests instrumentés Compose sur émulateur (connexion, éclairage, volets, thème) exécutés par `./gradlew connectedDebugAndroidTest`, tous contre un serveur simulé.

#### Scenario: Suite locale
- **WHEN** on lance les tests unitaires puis instrumentés sur un émulateur
- **THEN** tous passent sans accès réseau à un serveur Essensys réel

### Requirement: Garde no-armoire
En test, un intercepteur SHALL faire échouer immédiatement toute requête de mutation vers un hôte réel (`/api/portal/inject`, `/api/admin/inject`, `/web/actions`, `/scenarios/*/launch`) — seules les requêtes vers le serveur simulé local sont autorisées.

#### Scenario: Fuite vers un hôte réel
- **WHEN** un test tente d'envoyer une injection vers `mon.essensys.fr`
- **THEN** le test échoue avec un message « no-armoire : mutation réelle interdite »

### Requirement: Non-régression
Les comportements critiques SHALL être couverts par des tests `NR-android-<n>` référençant l'issue d'origine : couples (k, v) éclairage et volets, 401 → déconnexion, 409 → changement de mot de passe, absence de mode démo implicite, en-tête dry-run. Ils SHALL être consolidés par `nonreg_report.py` et exécutés en CI via `nonreg.yml`.

#### Scenario: Régression d'indice
- **WHEN** une modification change l'indice envoyé pour « allumer salon »
- **THEN** le test `NR-android-*` correspondant échoue et le rapport NR le signale

### Requirement: Checkup
`/checkup essensys-android-phone-apps` SHALL être vert avant toute PR : build, lint, unitaires, NR, instrumentés sur émulateur et captures des écrans connexion, éclairage, volets et réglages (clair et sombre).

#### Scenario: Rapport sur l'issue
- **WHEN** le checkup est vert
- **THEN** le rapport et les captures sont postés sur l'issue Feature #11

### Requirement: Livraison APK signé
La release SHALL produire un APK signé avec un keystore conservé hors dépôt (chiffré SOPS dans `essensys-ansible/secrets/`), versionné (`versionName` sémantique, `versionCode` croissant), publié en release GitHub avec son empreinte SHA-256 et des instructions d'installation. Aucun APK, keystore ou dump `.hprof` NE SHALL être versionné dans le dépôt.

#### Scenario: Publication
- **WHEN** la version 2.0.0 est publiée
- **THEN** la release GitHub contient l'APK signé, son SHA-256 et la procédure d'installation, et le dépôt ne contient aucun binaire

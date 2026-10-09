## 1. Socle projet et outillage de test (#1)

- [x] 1.1 Nettoyer le dépôt : retirer de l'arbre `mon.essensys.v.1.0.0.apk`, les `java_pid*.hprof` et `app/release/`, et compléter `.gitignore` (`*.apk`, `*.hprof`, `*.keystore`, `app/release/`) ; vérifier `git ls-files | grep -E 'apk|hprof'` vide
- [x] 1.2 Monter la stack : AGP, Kotlin et Compose BOM stables récents, Gradle wrapper à jour, `compileSdk`/`targetSdk` au niveau Play Store actuel, Java 17, retrait de Retrofit, ajout de kotlinx.serialization, DataStore, MockWebServer et compose-ui-test ; vérifier `./gradlew assembleDebug lintDebug`
- [x] 1.3 Créer l'AVD `essensys-pixel` (image système arm64 présente) et vérifier `/checkup` sur l'app existante (baseline : build OK, 0 test)
- [x] 1.4 Mettre en place `app/src/test` et `app/src/androidTest`, `AppContainer` de test, `NoArmoireInterceptor` et fixtures JSON portées de `mockFetch.ts` ; vérifier un test qui échoue sur une mutation vers un hôte réel (`NR-android-1`)

## 2. Couche data et authentification (#2)

- [x] 2.1 Client HTTP (OkHttp + kotlinx.serialization) et chaîne d'intercepteurs (auth, dry-run, 401/409, no-armoire) ; tests unitaires MockWebServer
- [x] 2.2 Stockage sécurisé (DataStore + clé Keystore) et purge de `EssensysPrefs` v1.0.0 ; test instrumenté de migration
- [x] 2.3 `AuthRepository` cloud (`/api/auth/login`, 401, 409 → `/api/auth/password/change`, logout) ; tests unitaires (`NR-android-2` 401 → déconnexion, `NR-android-3` 409 → changement de mot de passe)
- [x] 2.4 `AuthRepository` LAN (`/api/auth/login` gateway, cookie `essensys_lan_session`) et épinglage TOFU de la CA (D4) ; tests unitaires avec certificat de test

## 3. Modes de connexion et session (#3)

- [x] 3.1 Écran de connexion (choix Cloud / Réseau local, hôte LAN, refus de `http://`) et écran de changement de mot de passe obligatoire ; tests instrumentés
- [x] 3.2 Gate de liaison armoire (`link-request/status`, soumission) en cloud ; test instrumenté avec un compte sans armoire
- [x] 3.3 `SessionRepository` : état de la gateway (60 s) et dernière action (2 s), liés au cycle de vie, en-tête de mode et d'état ; tests unitaires du polling et de son arrêt en arrière-plan
- [x] 3.4 Supprimer le mode démo implicite et ajouter l'écran d'erreur « Connexion impossible » + « Réessayer » ; `NR-android-4`

## 4. Commandes Éclairage et Volets (#4)

- [x] 4.1 `IndexTable` (couples du portail, D5) et `ControlRepository` cloud et LAN ; tests `NR-android-5` (éclairage) et `NR-android-6` (volets) qui vérifient chaque couple (k, v)
- [x] 4.2 Écran Éclairage (par pièce, tout allumer / éteindre, résultat, anti-rebond) ; tests instrumentés
- [x] 4.3 Écran Volets (par volet, par zone, tout, temps de course en lecture) ; tests instrumentés
- [x] 4.4 Mode test dry-run (réglage, bandeau, en-tête, message `test_ok`) ; `NR-android-7`
- [x] 4.5 Signaler dans `essensys-memory/wiki/concepts/table-d-echange.md` l'écart des indices volets (wiki contre code portail)

## 5. Thème et finitions (#5)

- [x] 5.1 `EssensysTheme` (tokens clair et sombre du portail, formes, `CompositionLocal` success/warning) et choix Système / Clair / Sombre ; tests instrumentés de rendu
- [x] 5.2 Accueil et navigation V1 (Éclairage, Volets, Réglages ; tuile « Bientôt disponible » pour la V2), retrait des écrans factices ; vérifier à 360 dp et sur tablette
- [x] 5.3 Réglages (mode, thème, mode test, déconnexion, version) ; test instrumenté

## 6. Qualité, CI et livraison (#6)

- [ ] 6.1 Workflow CI : build, lint, tests unitaires, `nonreg.yml` réutilisable (`./gradlew testDebugUnitTest`, résultats `app/build/test-results/**/*.xml`) ; vérifier un run vert sur la PR
- [ ] 6.2 `/checkup essensys-android-phone-apps` vert, captures (connexion, éclairage, volets, réglages, clair et sombre) postées sur #11
- [x] 6.3 Keystore de release créé et chiffré SOPS dans `essensys-ansible/secrets/`, `signingConfig` lue depuis l'environnement ; vérifier qu'un `assembleRelease` sans variables échoue clairement
- [ ] 6.4 Workflow `release.yml` (tag `android-v*` : build signé, SHA-256, release GitHub avec instructions) ; vérifier sur `android-v2.0.0-rc1`
- [x] 6.5 README, captures et guide d'installation client (sideload, empreinte, mode cloud / LAN) mis à jour
- [ ] 6.6 Livrer la v2.0.0 au client et recueillir son retour en commentaire de #11

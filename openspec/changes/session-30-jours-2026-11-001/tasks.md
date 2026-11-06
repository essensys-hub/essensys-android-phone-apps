## 1. Connexion avec option et stockage (#35)

- [x] 1.1 Ajouter `rememberToken` (chiffré) et `rememberExpiresAt` à l'état de session et à `PersistentSessionStore` ; vérifier par un test instrumenté (le jeton survit à la réouverture, n'est pas lisible en clair dans le stockage, disparaît avec `clearSession`)
- [x] 1.2 Étendre `AuthRepository.login` avec `rememberMe` : lecture de `remember_token` et `remember_expires_at` (cloud), de `remember_expires_at` (réseau local), repli sans erreur si le serveur ne les renvoie pas ; vérifier par des tests unitaires MockWebServer des trois cas
- [x] 1.3 Ajouter la case « Se souvenir de moi (30 jours) » à l'écran de connexion, décochée par défaut et non mémorisée, en mode Cloud et en mode Réseau local ; vérifier par un test UI (case décochée après déconnexion, présente dans les deux modes, valeur transmise à la connexion)

## 2. Renouvellement et fin de session (#36)

- [x] 2.1 Renouveler l'accès cloud après un 401 avec verrou unique et un seul rejeu ; vérifier par des tests MockWebServer (accès expiré puis succès, 4 requêtes simultanées pour 1 seul renouvellement, `remember_invalid` qui efface la session et affiche l'écran de connexion avec le message « session terminée »)
- [x] 2.2 Révoquer la session à la déconnexion (envoi du jeton mémorisé, effacement local même sans réseau) et vérifier l'effacement après un changement de mot de passe ; vérifier par des tests unitaires (déconnexion hors ligne sans erreur, mot de passe changé ailleurs : renouvellement refusé puis connexion demandée)
- [x] 2.3 Afficher « Connecté jusqu'au <date> » dans les Réglages quand une session mémorisée est active, sans rien afficher sinon ; vérifier par un test UI (date en heure locale, absence pour une session non mémorisée)

## 3. Qualité et livraison (#37)

- [x] 3.1 Ajouter `NR-android-22` (aucune prolongation par l'usage : renouvellements quotidiens pendant 29 jours, refus le 31e) et `NR-android-23` (ni mot de passe, ni jeton, ni session mémorisée dans le journal et le bundle Support) avec une horloge contrôlée ; vérifier par leur passage dans les tests unitaires, puis les scénarios de reprise et de fin de session sur émulateur dans les deux modes, sans armoire réelle
- [ ] 3.2 CI verte, `/checkup` vert, captures de l'écran de connexion et des Réglages sur #39, README et version 2.4.0
- [ ] 3.3 Une fois les backends déployés : vérification réelle par un humain (connexion cochée, redémarrage du téléphone, lendemain), puis release `android-v2.4.0`

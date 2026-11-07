## 5. App Android (essensys-android-phone-apps) (#47)

- [x] 5.1 Ajouter la lecture des noms de volets (cloud et réseau local, repli sur les noms d'origine) et le nom affiché dans `ComfortRepository` ; vérifier par des tests unitaires (nom personnalisé, repli, nom spécial affiché tel quel, guillemets)
- [x] 5.2 Afficher le nom partout dans `ShuttersScreen.kt` : liste des deux groupes, confirmations d'ouverture et de fermeture, carte « Temps de course » ; vérifier par des tests d'interface (les `testTag` `time-current-<id>` de `TravelTimeFlowTest` restent valables)
- [x] 5.3 Ajouter le champ « Nom » et « Nom d'origine » pour l'administrateur avec les écritures cloud et local, et les messages d'erreur ; vérifier par des tests unitaires et instrumentés (`NR-android-26`, aucune commande vers l'armoire)
- [ ] 5.4 Préparer la version 2.7.0 (notes, tag `android-v2.7.0`) ; vérifier par `testDebugUnitTest`, `lintDebug` et `check_release_apk.py` — notes et README faits, le tag reste à poser

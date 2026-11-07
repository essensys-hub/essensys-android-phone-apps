## 7. App Android (essensys-android-phone-apps) (#45)

- [x] 7.1 Ajouter la lecture des noms (cloud et réseau local, repli sur les noms d'origine) et le nom affiché dans `ComfortRepository` ; vérifier par des tests unitaires (nom personnalisé, repli, nom spécial affiché tel quel)
- [x] 7.2 Afficher le nom partout : listes d'éclairage, fiche, écran Temporisations, éditeur de scénarios, confirmations ; vérifier par des tests d'interface
- [x] 7.3 Ajouter le champ « Nom » et « Nom d'origine » pour l'administrateur avec les écritures cloud et local, et les messages d'erreur ; vérifier par des tests unitaires et instrumentés (`NR-android-25`)
- [ ] 7.4 Préparer la version 2.6.0 (notes, tag `android-v2.6.0`) ; vérifier par `testDebugUnitTest`, `lintDebug` et `check_release_apk.py` — notes et README faits, le tag reste à poser

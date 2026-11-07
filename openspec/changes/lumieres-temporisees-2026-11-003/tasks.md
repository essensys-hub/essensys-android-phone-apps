## 6. App Android (essensys-android-phone-apps) (#43)

- [x] 6.1 Décoder le rôle à la connexion (cloud et LAN), le mémoriser dans `SessionState` (`isAdmin`) ; vérifier par des tests unitaires (admin, utilisateur simple, rôle absent donne non administrateur)
- [x] 6.2 Ajouter le catalogue, les raccourcis et la validation (`LightTimer`), la lecture et l'écriture (`ComfortRepository`) cloud et LAN, et la relecture à 5, 10 et 20 s ; vérifier par des tests MockWebServer (NR-android-24 : clés, plage 0 à 255)
- [x] 6.3 Ajouter l'écran « Temporisations » (nouvelle `Feature`, tuile d'accueil) et le réglage dans les lumières concernées ; vérifier par des tests instrumentés (liste, Dressing, valeur libre 7, 300 refusé, masqué pour un utilisateur simple)

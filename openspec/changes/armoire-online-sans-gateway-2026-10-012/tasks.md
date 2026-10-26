## 1. Règle armoire en ligne (#24)

- [ ] 1.1 Ajouter `online` à `ArmoireInfo` et la règle `armoireOnline()` ; l'utiliser dans `PortalRepository` et `SessionViewModel` ; vérifier par `NR-android-19` (test unitaire, cas A à F) et un test `PortalRepository` avec et sans gateway
- [ ] 1.2 Faire servir à `FakeBackend` une session sans gateway et un `last_seen` récent ou ancien ; vérifier par des tests instrumentés (cas D et E : badge et boutons)

## 2. Qualité et livraison (#25)

- [ ] 2.1 CI verte, `/checkup` vert, README et version 2.2.1
- [ ] 2.2 Validation sur le banc BP par un humain (sans gateway et avec gateway), release `android-v2.2.1`, puis email de suivi à l'utilisateur pilote

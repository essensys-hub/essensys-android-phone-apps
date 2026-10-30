## 1. Journal et diagnostics (#28)

- [x] 1.1 Ajouter `AppLog` (anneau de 500 entrées, persisté en JSON lignes) et le `LoggingInterceptor` avant `SessionInterceptor` ; vérifier par des tests unitaires (rotation, relecture après redémarrage, aucun en-tête ni corps enregistré)
- [x] 1.2 Ajouter `SupportDiagnostics` (appareil, réseau, temps de réponse HTTPS, portail, gateway, réglages) avec délais par test ; vérifier par des tests MockWebServer (health OK, délai dépassé, 401, LAN injoignable, mode test) et l'absence de tout appel `inject`

## 2. Bundle, écran Support et email (#29)

- [x] 2.1 Ajouter `SupportBundle` (zip `resume.txt`, `diagnostics.json`, `journal.jsonl`) et le `FileProvider` ; vérifier par un test unitaire du contenu et du nom du zip, et par `NR-android-20` (aucun token, cookie, mot de passe ni clé d'accès dans le zip)
- [x] 2.2 Ajouter l'écran Support (depuis Réglages et l'écran de connexion), le résumé et l'intent email vers `BuildConfig.SUPPORT_EMAIL` avec repli sur le partage ; vérifier par `SupportFlowTest` (intention : destinataire, objet, pièce jointe, sélecteur `mailto:` ; écran depuis Réglages et avant connexion)

## 3. Qualité et livraison (#30)

- [ ] 3.1 CI verte, `/checkup` vert, captures de l'écran Support sur #38, README et version 2.3.0
- [ ] 3.2 Vérification réelle par un humain (Wi-Fi puis 4G, Gmail et Outlook) avec réception du zip à l'adresse support, puis release `android-v2.3.0`
